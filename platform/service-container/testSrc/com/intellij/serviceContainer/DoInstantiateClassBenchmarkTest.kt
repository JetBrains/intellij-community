// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Proxy
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Compares the two `doInstantiateClass` strategies over [benchServiceClasses] (100 classes with `()`, `(Project)`, and
 * `(Project, CoroutineScope)` constructors):
 *
 * - [instantiateOld]: the pre-refactor approach — resolve each candidate signature via [MethodHandles.Lookup.findConstructor],
 *   which throws (and fills in a stack trace) for every signature the class does not declare.
 * - [instantiateNew]: the current approach — scan a single [Class.getDeclaredConstructors] snapshot with the production
 *   [findConstructorOrNull] / [newInstanceOrThrow] helpers, never throwing to probe.
 *
 * Both probe the signatures in the same order, so the only difference is the discovery mechanism.
 */
class DoInstantiateClassBenchmarkTest {
  private val projectType: MethodType = MethodType.methodType(Void.TYPE, Project::class.java)
  private val projectAndScopeType: MethodType = MethodType.methodType(Void.TYPE, Project::class.java, CoroutineScope::class.java)
  private val emptyType: MethodType = MethodType.methodType(Void.TYPE)

  private val benchLookup: MethodHandles.Lookup = MethodHandles.lookup()

  // Constructors only store the arguments and never dereference them, so a no-op proxy is enough.
  private val fakeProject: Project = Proxy.newProxyInstance(
    javaClass.classLoader, arrayOf(Project::class.java)
  ) { _, _, _ -> null } as Project
  // A throwaway scope: constructors only store it, nothing is ever launched in it.
  @Suppress("SSBasedInspection")
  private val scope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)

  // --- old strategy: java.lang.invoke, exception-driven discovery -------------------------------------------------

  private fun oldFindConstructorOrNull(lookup: MethodHandles.Lookup, clazz: Class<*>, type: MethodType): MethodHandle? {
    return try {
      lookup.findConstructor(clazz, type)
    }
    catch (_: NoSuchMethodException) {
      null
    }
    catch (_: IllegalAccessException) {
      null
    }
  }

  private fun instantiateOld(aClass: Class<*>): Any {
    val lookup = MethodHandles.privateLookupIn(aClass, benchLookup)
    return oldFindConstructorOrNull(lookup, aClass, projectType)?.invoke(fakeProject)
           ?: oldFindConstructorOrNull(lookup, aClass, projectAndScopeType)?.invoke(fakeProject, scope)
           ?: oldFindConstructorOrNull(lookup, aClass, emptyType)?.invoke()
           ?: error("Cannot find suitable constructor for ${aClass.name}")
  }

  // --- new strategy: plain reflection, using the production helpers -----------------------------------------------

  private fun instantiateNew(aClass: Class<*>): Any {
    val constructors = aClass.declaredConstructors
    return constructors.findConstructorOrNull(projectType)?.newInstanceOrThrow(fakeProject)
           ?: constructors.findConstructorOrNull(projectAndScopeType)?.newInstanceOrThrow(fakeProject, scope)
           ?: constructors.findConstructorOrNull(emptyType)?.newInstanceOrThrow()
           ?: error("Cannot find suitable constructor for ${aClass.name}")
  }

  @Test
  fun `new reflection-based instantiation is faster than the MethodHandles-based one`() {
    // Sanity: both strategies must actually produce instances of the requested class before we time anything.
    for (aClass in benchServiceClasses) {
      assertThat(instantiateOld(aClass)).isInstanceOf(aClass)
      assertThat(instantiateNew(aClass)).isInstanceOf(aClass)
    }

    // Warm up JIT, constructor-accessor inflation, and the exception paths of the old strategy.
    repeat(WARMUP_PASSES) {
      for (aClass in benchServiceClasses) {
        blackhole += System.identityHashCode(instantiateOld(aClass))
        blackhole += System.identityHashCode(instantiateNew(aClass))
      }
    }

    var oldTotalNs = 0L
    var newTotalNs = 0L
    // Alternate strategies per round so neither is systematically favored by GC or CPU-frequency drift.
    repeat(ROUNDS) {
      oldTotalNs += measure { instantiateOld(it) }
      newTotalNs += measure { instantiateNew(it) }
    }

    val instantiations = ROUNDS.toLong() * PASSES_PER_ROUND * benchServiceClasses.size
    val speedup = oldTotalNs.toDouble() / newTotalNs.toDouble()
    println(
      """
      |doInstantiateClass benchmark over ${benchServiceClasses.size} classes
      |  instantiations per strategy: $instantiations
      |  old (MethodHandles.findConstructor): ${oldTotalNs / 1_000_000} ms  (${oldTotalNs / instantiations} ns/instantiation)
      |  new (getDeclaredConstructors scan):  ${newTotalNs / 1_000_000} ms  (${newTotalNs / instantiations} ns/instantiation)
      |  speedup: ${"%.1f".format(speedup)}x
      """.trimMargin()
    )

    assertThat(newTotalNs)
      .describedAs("new reflection-based instantiation (%d ms) should be faster than MethodHandles-based (%d ms)",
                   newTotalNs / 1_000_000, oldTotalNs / 1_000_000)
      .isLessThan(oldTotalNs)
  }

  private inline fun measure(instantiate: (Class<*>) -> Any): Long {
    val start = System.nanoTime()
    var sink = 0
    repeat(PASSES_PER_ROUND) {
      for (aClass in benchServiceClasses) {
        sink += System.identityHashCode(instantiate(aClass))
      }
    }
    blackhole += sink
    return System.nanoTime() - start
  }

  companion object {
    private const val WARMUP_PASSES = 200
    private const val ROUNDS = 5
    private const val PASSES_PER_ROUND = 500

    // Consume instances so the JIT cannot dead-code-eliminate the instantiation calls being measured.
    @JvmStatic
    @Suppress("unused")
    private var blackhole: Int = 0
  }
}
