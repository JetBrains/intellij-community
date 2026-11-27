// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.parallel.Isolated
import org.junit.platform.commons.support.AnnotationSupport
import kotlin.jvm.optionals.getOrNull

private const val PARALLEL_EXECUTION_ENABLED = "junit.jupiter.execution.parallel.enabled"

/** Whether the run of this context executes its tests in parallel. */
@TestOnly
internal fun ExtensionContext.isParallelRun(): Boolean =
  getConfigurationParameter(PARALLEL_EXECUTION_ENABLED).getOrNull()?.toBoolean() == true

/**
 * Whether other tests can run at the same time as the test or the container of this context: the run executes in
 * parallel, and no class of the context is [Isolated], such as a class with
 * [TestApplication][com.intellij.testFramework.junit5.TestApplication].
 *
 * A check of the state of the whole JVM, such as of its threads or its Swing timers, cannot tell the state of such a test
 * from the state of the other tests, so it skips such a test.
 */
@TestOnly
internal fun ExtensionContext.runsBesideOtherTests(): Boolean {
  if (!isParallelRun()) {
    return false
  }
  return generateSequence(this) { it.parent.getOrNull() }
    .mapNotNull { it.element.getOrNull() as? Class<*> }
    .none { AnnotationSupport.isAnnotated(it, Isolated::class.java) }
}
