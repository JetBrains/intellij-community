// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.testFramework.junit5.AnalysisHostRuntime
import com.intellij.testFramework.junit5.AnalysisHostTest
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.support.AnnotationSupport
import org.junit.platform.commons.support.HierarchyTraversalMode
import java.lang.reflect.AnnotatedElement
import kotlin.jvm.optionals.getOrNull

/**
 * Calls the extensions of the current [AnalysisHostRuntime], and applies [AnalysisHostTest.hosts].
 */
@TestOnly
internal class AnalysisHostTestExtension : ExecutionCondition, BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {
  /**
   * Checks the annotation of the current element only. JUnit evaluates the class first, and a disabled class runs no method.
   * So the annotation on a method can only narrow the hosts of the class.
   * The class checks its methods for this rule, because JUnit evaluates no method of a disabled class.
   */
  override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
    val host = AnalysisHostRuntime.current().host
    val element = context.element.getOrNull()
    val annotation = element?.let { findAnnotation(it) }
    if (annotation != null && element is Class<*>) {
      checkMethodsNarrowClassHosts(element, annotation)
    }
    if (annotation != null && host !in annotation.hosts) {
      return ConditionEvaluationResult.disabled("Runs only on ${annotation.hosts.contentToString()}")
    }
    return ConditionEvaluationResult.enabled("Runs on $host")
  }

  private fun checkMethodsNarrowClassHosts(testClass: Class<*>, classAnnotation: AnalysisHostTest) {
    for (method in AnnotationSupport.findAnnotatedMethods(testClass, AnalysisHostTest::class.java, HierarchyTraversalMode.TOP_DOWN)) {
      val excluded = findAnnotation(method)!!.hosts.filter { it !in classAnnotation.hosts }
      check(excluded.isEmpty()) {
        "${testClass.simpleName}.${method.name} lists $excluded, but the class runs only on ${classAnnotation.hosts.contentToString()}. " +
        "The annotation on a method can only narrow the hosts of the class."
      }
    }
  }

  private fun findAnnotation(element: AnnotatedElement): AnalysisHostTest? =
    AnnotationSupport.findAnnotation(element, AnalysisHostTest::class.java).getOrNull()

  override fun beforeAll(context: ExtensionContext) {
    extensions().forEach(::checkCallbacksOnly)
    extensions().filterIsInstance<BeforeAllCallback>().forEach { it.beforeAll(context) }
  }

  // This extension calls only the callbacks. JUnit would not see another interface of a runtime extension.
  private fun checkCallbacksOnly(extension: Extension) {
    val unsupported = extensionInterfaces(extension.javaClass) - CALLBACKS
    check(unsupported.isEmpty()) {
      "${extension.javaClass.name} implements ${unsupported.map { it.simpleName }}, " +
      "but an AnalysisHostRuntime extension can implement only ${CALLBACKS.map { it.simpleName }}"
    }
  }

  private fun extensionInterfaces(type: Class<*>): Set<Class<*>> {
    val result = mutableSetOf<Class<*>>()
    val pending = ArrayDeque(listOf(type))
    while (pending.isNotEmpty()) {
      val current = pending.removeFirst()
      current.superclass?.let(pending::addLast)
      for (implemented in current.interfaces) {
        if (Extension::class.java.isAssignableFrom(implemented) && implemented != Extension::class.java && result.add(implemented)) {
          pending.addLast(implemented)
        }
      }
    }
    return result
  }

  override fun beforeEach(context: ExtensionContext) {
    extensions().filterIsInstance<BeforeEachCallback>().forEach { it.beforeEach(context) }
  }

  override fun afterEach(context: ExtensionContext) {
    extensions().asReversed().filterIsInstance<AfterEachCallback>().forEach { it.afterEach(context) }
  }

  override fun afterAll(context: ExtensionContext) {
    extensions().asReversed().filterIsInstance<AfterAllCallback>().forEach { it.afterAll(context) }
  }

  private fun extensions() = AnalysisHostRuntime.current().extensions
}

private val CALLBACKS: Set<Class<*>> = setOf(
  BeforeAllCallback::class.java, AfterAllCallback::class.java, BeforeEachCallback::class.java, AfterEachCallback::class.java,
)
