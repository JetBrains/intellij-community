// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase

abstract class ThreadingConcurrencyInspectionTestBase : LightDevKitInspectionFixTestBase() {

  private val inspection = ThreadingConcurrencyInspection()

  override fun getFileExtension()= "dummy"

  override fun setUp() {
    super.setUp()

    addThreadingAnnotations()

    myFixture.enableInspections(inspection)
  }

  protected fun runWithRequiresReadLockInsideRequiresEdtEnabled(func: () -> Unit) {
    try {
      inspection.requiresReadLockInsideRequiresEdt = true;
      func()
    }
    finally {
      inspection.requiresReadLockInsideRequiresEdt = false;
    }
  }

  protected fun runWithRequiresWriteLockInsideRequiresEdtEnabled(func: () -> Unit) {
    try {
      inspection.requiresWriteLockInsideRequiresEdt = true;
      func()
    }
    finally {
      inspection.requiresWriteLockInsideRequiresEdt = false;
    }
  }

  protected fun runWithCheckUnannotatedMethodsEnabled(func: () -> Unit) {
    try {
      inspection.checkMissingAnnotations = true;
      func()
    }
    finally {
      inspection.checkMissingAnnotations = false;
    }
  }

  private fun addThreadingAnnotations() {
    addAnnotation("RequiresBackgroundThread")
    addAnnotation("RequiresEdt")
    addAnnotation("RequiresReadLock")
    addAnnotation("RequiresReadLockAbsence")
    addAnnotation("RequiresWriteLock")
  }

  private fun addAnnotation(annotationShortName: String) {
    myFixture.addClass("""
      package com.intellij.util.concurrency.annotations;
      
      public @interface $annotationShortName {}
    """.trimIndent())
  }

  protected fun addEventDispatcherClass() {
    myFixture.addClass("""
        package com.intellij.util;
        
        public final class EventDispatcher<T extends java.util.EventListener> {
            public T getMulticaster() { return null;}
        } 
      """.trimIndent())
  }

}