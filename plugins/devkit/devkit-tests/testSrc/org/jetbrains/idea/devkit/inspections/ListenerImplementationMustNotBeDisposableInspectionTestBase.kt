// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

abstract class ListenerImplementationMustNotBeDisposableInspectionTestBase : PluginModuleTestCase() {

  protected abstract fun getFileExtension(): String

  override fun setUp() {
    super.setUp()
    addPlatformClasses()
    setPluginXml("plugin.xml")
    myFixture.enableInspections(ListenerImplementationMustNotBeDisposableInspection())
  }

  private fun addPlatformClasses() {
    myFixture.addClass(
      """      
      package com.intellij.openapi;
  
      public interface Disposable { }
      """.trimIndent()
    )

    myFixture.addClass(
      """      
      public interface BaseListenerInterface { }
      """.trimIndent()
    )

  }

  protected open fun doTest() {
    myFixture.testHighlighting(getTestName(false) + '.' + getFileExtension())
  }

}