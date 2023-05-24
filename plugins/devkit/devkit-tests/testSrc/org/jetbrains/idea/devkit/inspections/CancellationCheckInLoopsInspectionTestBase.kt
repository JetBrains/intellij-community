// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase

abstract class CancellationCheckInLoopsInspectionTestBase : LightDevKitInspectionFixTestBase() {

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      package com.intellij.util.concurrency.annotations; 
      
      public @interface RequiresReadLock { }
    """.trimIndent()
    )

    myFixture.addClass("""
      package inspections.cancellationCheckInLoops;
      
      public final class Foo {
        public static void doSomething() { }
      }
    """.trimIndent()
    )

    myFixture.addClass("""
      package com.intellij.openapi.progress;
      
      public abstract class ProgressManager {
        public static void checkCanceled() { }
      }
    """.trimIndent()
    )

    myFixture.enableInspections(CancellationCheckInLoopsInspection())
  }

}
