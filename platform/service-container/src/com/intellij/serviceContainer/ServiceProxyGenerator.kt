// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.io.toByteArray
import com.intellij.util.lang.ClassPath.ClassDataConsumer
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.util.LinkedList

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
 * Proxy generator for services.
 *
 * Generates a proxy class that:
 * - implements the requested service interface (or extends the given base class)
 * - implements [ServiceProxyInstrumentation]
 * - forwards all method calls to an underlying delegate instance
 * - can be dynamically reconfigured to forward to a different delegate via
 *   [ServiceProxyInstrumentation.setForwarding]
 *
 * Generated proxies are intended to be used as service instances when dynamic
 * service overrides are enabled (i.e., the service is open and the JVM flags
 * allows overrides).
 *
 * The primary purpose of these proxies is to tolerate leaked service references.
 * When a service is overridden, an existing (“old”) instance can be updated so
 * that all of its public method calls are forwarded to the new instance. This
 * allows existing references to continue functioning correctly.
 *
 * Additionally, the forwarding mechanism (`forwardTo: Lazy<Any>?`) can be instrumented
 * to log stack traces when the old instance is accessed,
 * helping identify and fix the underlying reference leak.
 */
internal object ServiceProxyGenerator {
  fun <T> createInstance(
    superClass: Class<T>,
    delegate: Any,
  ): T {
    val loader = superClass.classLoader
    if (loader is ClassDataConsumer) {
      return createInstance(superClass, loader, delegate)
    }
    else if (ApplicationManager.getApplication()?.isUnitTestMode != false) {
      // for TeamCity which uses jdk.internal.loader.ClassLoaders$AppClassLoader at the moment
      class ClassLoaderWithClassDataConsumer(parent: ClassLoader) : ClassLoader(parent), ClassDataConsumer {
        override fun isByteBufferSupported(name: String?): Boolean = false

        override fun consumeClassData(name: String, data: ByteArray): Class<*>? {
          return defineClass(name, data, 0, data.size, null)
        }

        override fun consumeClassData(name: String, data: ByteBuffer): Class<*>? = consumeClassData(name, data.toByteArray())
      }

      return createInstance(superClass, ClassLoaderWithClassDataConsumer(loader), delegate)
    }
    else {
      LOG.error("Cannot use ${loader.javaClass} (the classloader of $superClass) to define a new class")
      return delegate as T
    }
  }

  private fun <T, CL> createInstance(
    superClass: Class<T>,
    loader: CL,
    delegate: Any,
  ): T where CL : ClassDataConsumer, CL : ClassLoader {
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