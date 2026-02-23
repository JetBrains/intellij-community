// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import com.intellij.junit.testFramework.JUnit3SuperTearDownInspectionTestBase
import com.intellij.junit.testFramework.JUnitLibrary
import com.intellij.junit.testFramework.JUnitProjectDescriptor
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.addRoot
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KotlinJUnit3SuperTearDownInspectionTest : JUnit3SuperTearDownInspectionTestBase(), ExpectedPluginModeProvider {

  protected open class KotlinJUnitProjectDescriptor : JUnitProjectDescriptor(LanguageLevel.HIGHEST, JUnitLibrary.JUNIT3) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      ConfigLibraryUtil.addLibrary(model, "KotlinJavaRuntime") {
        addRoot(KotlinArtifacts.kotlinStdlib, OrderRootType.CLASSES)
        addRoot(KotlinArtifacts.kotlinStdlibSources, OrderRootType.SOURCES)
      }
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = KotlinJUnitProjectDescriptor()

  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
  }

  fun `test teardown in finally no highlighting`() {
    myFixture.testHighlighting(
      JvmLanguage.KOTLIN, """
      class NoProblem : junit.framework.TestCase() {
        override fun tearDown() {
          super.tearDown();
        }
      }
      class CalledInFinally : junit.framework.TestCase() {
        override fun tearDown() {
          try {
            System.out.println()
          } finally {
            super.tearDown()
          }
        }
      }
      class SomeTest : junit.framework.TestCase() {
        override fun setUp() {
          try {
            super.setUp()
          }
          catch (t: Throwable) {
            super.tearDown()
          }
        }
        fun test_something() { }
      }
    """.trimIndent())
  }

  fun `test teardown in finally highlighting`() {
    myFixture.testHighlighting(
      JvmLanguage.KOTLIN, """
      class SuperTearDownInFinally : junit.framework.TestCase() {
        override fun tearDown() {
          super.<warning descr="'tearDown()' is not called from 'finally' block">tearDown</warning>()
          System.out.println()
        }
      }      
    """.trimIndent())
  }
}