// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import org.jetbrains.idea.devkit.inspections.UseIntelliJVirtualThreadsInspection
import java.util.function.Supplier

abstract class UseIntelliJVirtualThreadsInspectionTestBase : LightDevKitInspectionFixTestBase() {

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return DefaultLightProjectDescriptor(Supplier { IdeaTestUtil.getMockJdk21() })
  }

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(UseIntelliJVirtualThreadsInspection())
    myFixture.addClass(
      """package com.intellij.concurrency.virtualThreads;
        |public final class IntelliJVirtualThreads { 
        |  private IntelliJVirtualThreads() {}  
        |  public static java.lang.Thread.Builder ofVirtual() { 
        |    return null;
        |  }
        |}""".trimMargin()
    )
  }
}