// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.wm.impl

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.graph.DFSTBuilder
import com.intellij.util.graph.OutboundSemiGraph
import com.intellij.util.ui.EDT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.classfile.Attributes
import java.lang.classfile.ClassFile
import java.lang.classfile.ClassModel
import java.lang.classfile.MethodModel
import java.lang.classfile.Opcode
import java.lang.classfile.instruction.FieldInstruction
import java.lang.classfile.instruction.InvokeInstruction
import java.lang.classfile.instruction.NewObjectInstruction
import java.lang.reflect.AccessFlag

@TestApplication
internal class FrameClassesWarmUpTest {
  private val classLoader = FrameClassesWarmUp::class.java.classLoader

  @Test
  fun `every class of the warm-up exists`() {
    val missing = buildList {
      addAll(COMMON_FRAME_CLASS_NAMES)
      addAll(MAC_FRAME_CLASS_NAMES)
      addAll(OTHER_OS_FRAME_CLASS_NAMES)
    }.filter { name ->
      classLoader.getResource(name.replace('.', '/') + ".class") == null
    }
    assertThat(missing).isEmpty()
  }

  @Test
  fun `every class is listed once and every load-only class is listed`() {
    val all = buildList {
      addAll(COMMON_FRAME_CLASS_NAMES)
      addAll(MAC_FRAME_CLASS_NAMES)
      addAll(OTHER_OS_FRAME_CLASS_NAMES)
    }
    assertThat(all).doesNotHaveDuplicates()
    assertThat(all).containsAll(LOAD_ONLY_FRAME_CLASS_NAMES)
  }

  @Test
  fun `the classes that the warm-up initializes initialize off the EDT`() {
    assertThat(EDT.isCurrentThreadEdt()).isFalse()
    val failed = frameClassNamesOfCurrentOs().filter { it !in LOAD_ONLY_FRAME_CLASS_NAMES }.mapNotNull { name ->
      runCatching { Class.forName(name, true, classLoader) }.exceptionOrNull()?.let { "$name: $it" }
    }
    assertThat(failed).isEmpty()
  }

  /**
   * The warm-up and the EDT initialize the same classes at the same time. The JVM holds one initialization lock per class.
   * Two threads that enter a cycle of the initialization graph at different classes thus wait for each other forever.
   * The test checks the classes of every OS, so that one agent also covers the macOS list.
   */
  @Test
  fun `the classes that the warm-up initializes reach no initialization cycle`() {
    val roots = buildList {
      addAll(COMMON_FRAME_CLASS_NAMES)
      addAll(MAC_FRAME_CLASS_NAMES)
    }.filter { it !in LOAD_ONLY_FRAME_CLASS_NAMES }.map { it.replace('.', '/') }
    assertThat(ClassInitializationGraph(classLoader).findCycles(roots)).isEmpty()
  }
}

/**
 * The class initialization graph of JVMS 5.5, read from the class files. An edge `C -> D` means that the initialization of `C`
 * initializes `D` in the same thread: `D` is the superclass of `C`, a superinterface of `C` with a default method,
 * or a class that a method reachable from `C.<clinit>` touches with `new`, `getstatic`, `putstatic` or `invokestatic`.
 *
 * A call is followed to the declaration on its static owner or a supertype of it, not to an override.
 * An `invokedynamic` is not followed, because a lambda body does not run when the lambda is created.
 * The classes of the JDK and of the Kotlin libraries are not read, because their initializers do not call IDE code.
 */
private class ClassInitializationGraph(private val classLoader: ClassLoader) {
  private val models = HashMap<String, ClassModel?>()
  private val methodInfos = HashMap<MethodRef, MethodInfo>()
  private val edges = HashMap<String, Map<String, String>>()

  /** Returns one line per cycle that the [roots] reach: the classes of the cycle with the reason of each edge. */
  fun findCycles(roots: List<String>): List<String> {
    val nodes = LinkedHashSet(roots)
    val queue = ArrayDeque(roots)
    while (queue.isNotEmpty()) {
      edgesOf(queue.removeFirst()).keys.filterTo(queue) { nodes.add(it) }
    }
    val graph = object : OutboundSemiGraph<String> {
      override fun getNodes(): Collection<String> = nodes

      override fun getOut(node: String): Iterator<String> = edgesOf(node).keys.iterator()
    }
    val rootSet = roots.toHashSet()
    return DFSTBuilder(graph).components.map { it.toHashSet() }.filter { it.size > 1 && !isHiddenInHost(it, rootSet) }.map { component ->
      "${describeCycle(component)}; reached by ${describePath(roots, component)}"
    }
  }

  /** The shortest path from one of the [roots] to the [component]. */
  private fun describePath(roots: List<String>, component: Set<String>): String {
    val previous = HashMap<String, String>()
    val queue = ArrayDeque(roots)
    val visited = roots.toHashSet()
    while (queue.isNotEmpty()) {
      val node = queue.removeFirst()
      if (node in component) {
        return generateSequence(node) { previous.get(it) }.toList().asReversed().joinToString(" -> ")
      }
      for (target in edgesOf(node).keys) {
        if (visited.add(target)) {
          previous.put(target, node)
          queue.addLast(target)
        }
      }
    }
    error("No root reaches $component")
  }

  /**
   * Another thread cannot start a cycle of a class and its anonymous or private nested classes. Only the code of the class
   * reaches such a nested class, and that code runs after the initialization of the class or in the same thread.
   * The warm-up itself is the exception: it starts each of the [roots], so a nested class among them is not hidden.
   */
  private fun isHiddenInHost(component: Set<String>, roots: Set<String>): Boolean {
    return component.any { host ->
      component.all { it == host || it.startsWith("$host$") && it !in roots && isAnonymousOrPrivate(it) }
    }
  }

  private fun isAnonymousOrPrivate(className: String): Boolean {
    val innerClass = model(className)?.findAttribute(Attributes.innerClasses())?.orElse(null)?.classes()
      ?.firstOrNull { it.innerClass().asInternalName() == className } ?: return false
    return innerClass.innerName().isEmpty || innerClass.flags().contains(AccessFlag.PRIVATE)
  }

  private fun describeCycle(component: Set<String>): String {
    val start = component.min()
    val previous = HashMap<String, String>()
    val queue = ArrayDeque(listOf(start))
    while (queue.isNotEmpty()) {
      val node = queue.removeFirst()
      for (target in edgesOf(node).keys) {
        if (target == start) {
          val path = generateSequence(node) { previous.get(it) }.toList().asReversed() + start
          val edges = path.zipWithNext { from, to -> "$from -> $to (${edgesOf(from).getValue(to)})" }.joinToString(", ")
          return "${component.sorted()}: $edges"
        }
        if (target in component && target !in previous) {
          previous.put(target, node)
          queue.addLast(target)
        }
      }
    }
    error("No cycle in $component")
  }

  /** The classes that the initialization of [className] initializes, each with the reason of the edge. */
  private fun edgesOf(className: String): Map<String, String> {
    edges.get(className)?.let { return it }
    val result = LinkedHashMap<String, String>()
    val model = model(className)
    if (model != null) {
      model.superclass().ifPresent { addEdge(result, className, it.asInternalName(), "superclass") }
      if (!model.flags().has(AccessFlag.INTERFACE)) {
        for (superinterface in superinterfacesWithDefaultMethods(model)) {
          addEdge(result, className, superinterface, "superinterface")
        }
      }

      val visited = HashSet<MethodRef>()
      val queue = ArrayDeque(listOf(MethodRef(className, "<clinit>", "()V")))
      while (queue.isNotEmpty()) {
        val method = queue.removeFirst()
        if (!visited.add(method)) {
          continue
        }
        val info = methodInfo(method)
        for (touched in info.touched) {
          addEdge(result, className, touched, "${method.owner}.${method.name}")
        }
        queue.addAll(info.calls)
      }
    }
    edges.put(className, result)
    return result
  }

  private fun addEdge(result: MutableMap<String, String>, from: String, to: String, reason: String) {
    if (to != from && model(to) != null) {
      result.putIfAbsent(to, reason)
    }
  }

  private fun superinterfacesWithDefaultMethods(model: ClassModel): Set<String> {
    val result = LinkedHashSet<String>()
    val queue = ArrayDeque(model.interfaces().map { it.asInternalName() })
    while (queue.isNotEmpty()) {
      val name = queue.removeFirst()
      val superinterface = model(name) ?: continue
      if (superinterface.methods().any { !it.flags().has(AccessFlag.ABSTRACT) && !it.flags().has(AccessFlag.STATIC) }) {
        result.add(name)
      }
      superinterface.interfaces().mapTo(queue) { it.asInternalName() }
    }
    return result
  }

  /** The classes that the code of [ref] touches, and the methods that it calls. A method without code touches nothing. */
  private fun methodInfo(ref: MethodRef): MethodInfo {
    methodInfos.get(ref)?.let { return it }
    val touched = LinkedHashSet<String>()
    val calls = ArrayList<MethodRef>()
    val code = resolveMethod(ref.owner, ref.name, ref.descriptor)?.code()?.orElse(null)
    for (element in code ?: emptyList()) {
      when (element) {
        is NewObjectInstruction -> touched.add(element.className().asInternalName())
        is FieldInstruction -> if (element.opcode() == Opcode.GETSTATIC || element.opcode() == Opcode.PUTSTATIC) {
          val owner = element.owner().asInternalName()
          touched.add(resolveFieldOwner(owner, element.name().stringValue()) ?: owner)
        }
        is InvokeInstruction -> {
          val owner = element.owner().asInternalName()
          if (!owner.startsWith('[')) {
            val call = MethodRef(owner, element.name().stringValue(), element.type().stringValue())
            if (element.opcode() == Opcode.INVOKESTATIC) {
              touched.add(resolveMethodOwner(owner, call.name, call.descriptor) ?: owner)
            }
            calls.add(call)
          }
        }
        else -> {}
      }
    }
    val info = MethodInfo(touched, calls)
    methodInfos.put(ref, info)
    return info
  }

  private fun resolveMethod(owner: String, name: String, descriptor: String): MethodModel? {
    // a class initializer is not inherited
    val declaringClass = (if (name == "<clinit>") owner else resolveMethodOwner(owner, name, descriptor)) ?: return null
    return model(declaringClass)?.methods()?.firstOrNull { it.methodName().equalsString(name) && it.methodType().equalsString(descriptor) }
  }

  private fun resolveMethodOwner(owner: String, name: String, descriptor: String): String? {
    val model = model(owner) ?: return null
    if (model.methods().any { it.methodName().equalsString(name) && it.methodType().equalsString(descriptor) }) {
      return owner
    }
    model.superclass().orElse(null)?.let { superclass -> resolveMethodOwner(superclass.asInternalName(), name, descriptor)?.let { return it } }
    return model.interfaces().firstNotNullOfOrNull { resolveMethodOwner(it.asInternalName(), name, descriptor) }
  }

  private fun resolveFieldOwner(owner: String, name: String): String? {
    val model = model(owner) ?: return null
    if (model.fields().any { it.fieldName().equalsString(name) }) {
      return owner
    }
    model.interfaces().firstNotNullOfOrNull { resolveFieldOwner(it.asInternalName(), name) }?.let { return it }
    return model.superclass().orElse(null)?.let { resolveFieldOwner(it.asInternalName(), name) }
  }

  private fun model(className: String): ClassModel? {
    if (className in models) {
      return models.get(className)
    }
    val model = if (IGNORED_PACKAGES.any { className.startsWith(it) }) {
      null
    }
    else {
      classLoader.getResourceAsStream("$className.class")?.use { ClassFile.of().parse(it.readAllBytes()) }
    }
    models.put(className, model)
    return model
  }

  private data class MethodRef(@JvmField val owner: String, @JvmField val name: String, @JvmField val descriptor: String)

  private class MethodInfo(@JvmField val touched: Set<String>, @JvmField val calls: List<MethodRef>)
}

private val IGNORED_PACKAGES = listOf("java/", "javax/", "jdk/", "sun/", "com/sun/", "kotlin/", "kotlinx/")
