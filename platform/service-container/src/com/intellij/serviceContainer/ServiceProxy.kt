// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.util.lang.ClassPath.ClassDataConsumer
import org.jetbrains.org.objectweb.asm.*
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*

internal interface ServiceProxyInstrumentation {
  fun setForwarding(forwardTo: Lazy<Any>?)
}

private const val METHOD_NAME_GET_DELEGATE = "getDelegate"
private const val METHOD_DESCRIPTOR_GET_DELEGATE = "()Ljava/lang/Object;"

private const val METHOD_NAME_SET_FORWARDING = "setForwarding"
private const val METHOD_DESCRIPTOR_SET_FORWARDING = "(Lkotlin/Lazy;)V"

private const val FIELD_NAME_FORWARD_TO = "forwardTo"
private const val FIELD_NAME_ORIGINAL_DELEGATE = "originalDelegate"

/**
 * ASM-based proxy generator for service interfaces.
 * Generates a class that:
 * - implements the requested service interface (or extends given base class) and implements [ServiceProxyInstrumentation]
 * - forwards all class/interface calls to an underlying delegate instance
 * - can be switched to forward to another delegate via [ServiceProxyInstrumentation.setForwarding]
 */
internal object ServiceProxy {
  @Suppress("UNCHECKED_CAST")
  fun <T> createInstance(
    superClass: Class<T>,
    delegate: Any,
  ): T {
    val loader = superClass.classLoader
    if (loader !is ClassDataConsumer) {
      LOG.error("Cannot use ${loader.javaClass} (the classloader of $superClass) to define a new class")
      return delegate as T
    }

    val proxyClassName = buildProxyClassName(superClass)
    val proxyClass = try {
      loader.loadClass(proxyClassName)
    }
    catch (_: ClassNotFoundException) {
      val bytes = generateProxyBytes(proxyClassName, superClass)
      loader.consumeClassData(proxyClassName, bytes)
    }

    val instance = proxyClass.getConstructor(Any::class.java).newInstance(delegate)
    return instance as T
  }

  private fun buildProxyClassName(superClass: Class<*>): String {
    // Place proxy in the SAME package as the target to preserve access to protected/package-private members
    val className = superClass.name
    val pkg = className.substringBeforeLast('.', "")
    val simple = className.substringAfterLast('.')
    val base = simple.replace('$', '_')
    val suffix = Integer.toHexString(System.identityHashCode(superClass))
    return if (pkg.isEmpty()) "${base}\$Proxy${suffix}" else "$pkg.${base}\$Proxy${suffix}"
  }

  private fun generateProxyBytes(classNameDot: String, target: Class<*>): ByteArray {
    // We generate the following class:
    // public class ClassName {
    //   private final @NotNull Object originalDelegate;
    //   private volatile @Nullable Lazy<Object> forwardTo;
    //
    //   ClassName(@NotNull Object delegate) {
    //     this.originalDelegate = delegate;
    //     this.forwardTo = null;
    //   }
    //
    //   private Object getDelegate() {
    //     return forwardTo?.value() ?: originalDelegate;
    //   }
    //
    //   public void setForwarding(@Nullable Lazy<Object> forwardTo) {
    //     this.forwardTo = forwardTo;
    //   }
    //
    //   // Implement all public non-final methods by delegating to getDelegate()
    //   (we don't delegate private, package-private or protected methods, because we can't guarantee
    //   that we have access to them from this proxy class. E.g., think about accessing package-private
    //   method in a class within the same package, but loaded with a different classloader - this will
    //   be either IllegalAccessError or VerifyError)
    // }

    val className = classNameDot.replace('.', '/')
    val targetInternal = Type.getInternalName(target)
    val spiInternal = Type.getInternalName(ServiceProxyInstrumentation::class.java)

    val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
    val superInternal = if (target.isInterface) "java/lang/Object" else targetInternal
    val interfaces = if (target.isInterface) arrayOf(targetInternal, spiInternal) else arrayOf(spiInternal)
    cw.visit(
      Opcodes.V1_8,
      Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER,
      className,
      null,
      superInternal,
      interfaces
    )

    // Fields
    cw.visitField(Opcodes.ACC_PRIVATE or Opcodes.ACC_FINAL, FIELD_NAME_ORIGINAL_DELEGATE, Type.getDescriptor(Any::class.java), null, null)
      .visitEnd()
    cw.visitField(Opcodes.ACC_PRIVATE or Opcodes.ACC_VOLATILE, FIELD_NAME_FORWARD_TO, Type.getDescriptor(Lazy::class.java), null, null)
      .visitEnd()

    // Constructor (Object delegate)
    generateConstructor(cw, target, targetInternal, className)

    // private Object getDelegate()
    generatePrivateGetDelegate(cw, className)

    // Implement ServiceProxyInstrumentation.setForwarding(Lazy)
    generateSetForwarding(cw, className)

    // Implement all non-static methods by delegating
    val methods = collectMethods(target)

    for (m in methods) {
      generateDelegatingMethod(m, cw, className)
    }

    cw.visitEnd()
    return cw.toByteArray()
  }

  private fun collectMethods(target: Class<*>): Collection<Method> {
    data class MethodSignature(val name: String, val parameterTypes: List<Class<*>>)

    val typesToVisit = LinkedList<Class<*>>()
    typesToVisit.add(target)

    val result: MutableMap<MethodSignature, Method> = mutableMapOf()

    // width-first traversal is important: we want to find and remember the most specific method signature if there are several overrides
    while (typesToVisit.isNotEmpty()) {
      val type = typesToVisit.pop()
      if (type == Any::class.java) continue

      if (type.superclass != null) {
        typesToVisit.add(type.superclass)
      }
      typesToVisit.addAll(type.interfaces)

      type.declaredMethods
        .filter { m ->
          val modifiers = m.modifiers
          Modifier.isPublic(modifiers) && !Modifier.isFinal(modifiers)
        }
        .forEach { m ->
          // skip if already added (from a subclass)
          result.putIfAbsent(MethodSignature(m.name, m.parameterTypes.toList()), m)
        }
    }

    return result.values
  }

  private fun generateDelegatingMethod(m: Method, cw: ClassWriter, className: String) {
    val modifiers = m.modifiers

    val name = m.name
    val methodDescriptor = Type.getMethodDescriptor(m)
    val exceptions = m.exceptionTypes.map { Type.getInternalName(it) }.toTypedArray().takeIf { it.isNotEmpty() }

    val access = if (Modifier.isPublic(modifiers)) Opcodes.ACC_PUBLIC
    else if (Modifier.isProtected(modifiers)) Opcodes.ACC_PROTECTED
    else 0 // package-private

    val mv: MethodVisitor = cw.visitMethod(access, name, methodDescriptor, null, exceptions)
    mv.visitCode()
    // cast getDelegate to proper receiver type and call method
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, className, METHOD_NAME_GET_DELEGATE, METHOD_DESCRIPTOR_GET_DELEGATE, false)
    mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(m.declaringClass))
    // load arguments
    var slot = 1
    for (paramType in m.parameterTypes.map { Type.getType(it) }) {
      when (paramType.sort) {
        Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> mv.visitVarInsn(Opcodes.ILOAD, slot)
        Type.FLOAT -> mv.visitVarInsn(Opcodes.FLOAD, slot)
        Type.LONG -> mv.visitVarInsn(Opcodes.LLOAD, slot)
        Type.DOUBLE -> mv.visitVarInsn(Opcodes.DLOAD, slot)
        else -> mv.visitVarInsn(Opcodes.ALOAD, slot)
      }
      slot += paramType.size
    }
    if (m.declaringClass.isInterface) {
      mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(m.declaringClass), name, methodDescriptor, true)
    }
    else {
      mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(m.declaringClass), name, methodDescriptor, false)
    }
    // return
    val retType = Type.getType(m.returnType)
    when (retType.sort) {
      Type.VOID -> mv.visitInsn(Opcodes.RETURN)
      Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> mv.visitInsn(Opcodes.IRETURN)
      Type.FLOAT -> mv.visitInsn(Opcodes.FRETURN)
      Type.LONG -> mv.visitInsn(Opcodes.LRETURN)
      Type.DOUBLE -> mv.visitInsn(Opcodes.DRETURN)
      else -> mv.visitInsn(Opcodes.ARETURN)
    }
    mv.visitMaxs(0, 0)
    mv.visitEnd()
  }

  private fun generateSetForwarding(cw: ClassWriter, className: String) {
    val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, METHOD_NAME_SET_FORWARDING, METHOD_DESCRIPTOR_SET_FORWARDING, null, null)
    mv.visitCode()
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitVarInsn(Opcodes.ALOAD, 1)
    mv.visitFieldInsn(Opcodes.PUTFIELD, className, FIELD_NAME_FORWARD_TO, Type.getDescriptor(Lazy::class.java))
    mv.visitInsn(Opcodes.RETURN)
    mv.visitMaxs(0, 0)
    mv.visitEnd()
  }

  private fun generatePrivateGetDelegate(cw: ClassWriter, className: String) {
    val mv = cw.visitMethod(Opcodes.ACC_PRIVATE, METHOD_NAME_GET_DELEGATE, METHOD_DESCRIPTOR_GET_DELEGATE, null, null)
    mv.visitCode()
    // Lazy f = this.forwardTo
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitFieldInsn(Opcodes.GETFIELD, className, FIELD_NAME_FORWARD_TO, Type.getDescriptor(Lazy::class.java))
    // if (f == null) goto useOriginal
    val useOriginal = Label()
    val after = Label()
    mv.visitJumpInsn(Opcodes.IFNULL, useOriginal)
    // return f.getValue()
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitFieldInsn(Opcodes.GETFIELD, className, FIELD_NAME_FORWARD_TO, Type.getDescriptor(Lazy::class.java))
    mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                       Type.getInternalName(Lazy::class.java),
                       "getValue",
                       "()${Type.getDescriptor(Any::class.java)}",
                       true)
    mv.visitJumpInsn(Opcodes.GOTO, after)
    // useOriginal:
    mv.visitLabel(useOriginal)
    // return this.originalDelegate
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitFieldInsn(Opcodes.GETFIELD, className, FIELD_NAME_ORIGINAL_DELEGATE, Type.getDescriptor(Any::class.java))
    mv.visitLabel(after)
    mv.visitInsn(Opcodes.ARETURN)
    mv.visitMaxs(0, 0)
    mv.visitEnd()
  }

  private fun generateConstructor(
    cw: ClassWriter,
    target: Class<*>,
    targetInternal: String?,
    className: String,
  ) {
    val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/Object;)V", null, null)
    mv.visitCode()
    // super()
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, if (target.isInterface) "java/lang/Object" else targetInternal, "<init>", "()V", false)
    // this.originalDelegate = delegate
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitVarInsn(Opcodes.ALOAD, 1)
    mv.visitFieldInsn(Opcodes.PUTFIELD, className, FIELD_NAME_ORIGINAL_DELEGATE, Type.getDescriptor(Any::class.java))
    // this.forwardTo = null
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitInsn(Opcodes.ACONST_NULL)
    mv.visitFieldInsn(Opcodes.PUTFIELD, className, FIELD_NAME_FORWARD_TO, Type.getDescriptor(Lazy::class.java))
    mv.visitInsn(Opcodes.RETURN)
    mv.visitMaxs(0, 0)
    mv.visitEnd()
  }
}