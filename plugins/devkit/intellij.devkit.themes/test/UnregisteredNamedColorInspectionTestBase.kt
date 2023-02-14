// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes

import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.ui.components.JBList
import com.intellij.util.PathUtil

abstract class UnregisteredNamedColorInspectionTestBase : JavaCodeInsightFixtureTestCase() {

  companion object {
    const val themeMetadata = "/themes/metadata/IntelliJPlatform.themeMetadata.json"
    const val knownNamedColor = "CompletionPopup.background"
  }

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>?) {
    val resourceRoot = PathManager.getResourceRoot(javaClass, themeMetadata)
    moduleBuilder!!.addLibrary("platform-resources", resourceRoot)
    moduleBuilder.addLibrary("platform-api", PathUtil.getJarPathForClass(JBList::class.java))
  }

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(UnregisteredNamedColorInspection())

    //language=JAVA
    myFixture.addClass("""
      package com.intellij.ui;

      public final class JBColor {
        public static void namedColor(String s, int i) {}
      }
    """.trimIndent())
  }

}
