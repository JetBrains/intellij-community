// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixture
import org.jetbrains.idea.devkit.inspections.quickfix.lightDevKitInspectionFixture

/**
 * The fixture of the [ThreadingConcurrencyInspection] tests. Create it with [threadingConcurrencyFixture].
 *
 * The fixture enables [ThreadingConcurrencyInspection]. The project has the stubs of the threading annotations.
 */
class ThreadingConcurrencyFixture internal constructor(
  fixture: LightJavaCodeInsightTestFixture,
  private val inspection: ThreadingConcurrencyInspection,
) :
  LightDevKitInspectionFixture(fixture, "dummy") {
  /**
   * Runs [func] with the `requiresReadLockInsideRequiresEdt` option of the inspection.
   */
  fun runWithRequiresReadLockInsideRequiresEdtEnabled(func: () -> Unit) {
    try {
      inspection.requiresReadLockInsideRequiresEdt = true
      func()
    }
    finally {
      inspection.requiresReadLockInsideRequiresEdt = false
    }
  }

  /**
   * Runs [func] with the `requiresWriteLockInsideRequiresEdt` option of the inspection.
   */
  fun runWithRequiresWriteLockInsideRequiresEdtEnabled(func: () -> Unit) {
    try {
      inspection.requiresWriteLockInsideRequiresEdt = true
      func()
    }
    finally {
      inspection.requiresWriteLockInsideRequiresEdt = false
    }
  }

  /**
   * Runs [func] with the `checkMissingAnnotations` option of the inspection.
   */
  fun runWithCheckUnannotatedMethodsEnabled(func: () -> Unit) {
    try {
      inspection.checkMissingAnnotations = true
      func()
    }
    finally {
      inspection.checkMissingAnnotations = false
    }
  }

  /**
   * Adds the stub of `EventDispatcher`.
   */
  fun addEventDispatcherClass() {
    fixture.addClass("""
        package com.intellij.util;

        public final class EventDispatcher<T extends java.util.EventListener> {
            public T getMulticaster() { return null;}
        }
      """.trimIndent())
  }
}

/**
 * Creates a [ThreadingConcurrencyFixture] for one test. Declare it as an instance field.
 *
 * [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun threadingConcurrencyFixture(
  projectFixture: TestFixture<LightJavaProject>,
  basePath: String = "",
): TestFixture<ThreadingConcurrencyFixture> = testFixture("threadingConcurrencyFixture") {
  val inspection = ThreadingConcurrencyInspection()
  val devKit = lightDevKitInspectionFixture(projectFixture, "dummy", basePath).init()
  val fixture = devKit.fixture
  addAnnotation(fixture, "RequiresBackgroundThread")
  addAnnotation(fixture, "RequiresEdt")
  addAnnotation(fixture, "RequiresReadLock")
  addAnnotation(fixture, "RequiresReadLockAbsence")
  addAnnotation(fixture, "RequiresWriteLock")
  fixture.enableInspections(inspection)
  initialized(ThreadingConcurrencyFixture(fixture, inspection)) {}
}

private fun addAnnotation(fixture: LightJavaCodeInsightTestFixture, annotationShortName: String) {
  fixture.addClass("""
    package com.intellij.util.concurrency.annotations;

    public @interface $annotationShortName {}
  """.trimIndent())
}
