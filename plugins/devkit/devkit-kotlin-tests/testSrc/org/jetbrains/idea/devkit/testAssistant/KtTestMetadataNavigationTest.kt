// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiElement
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil

class KtTestMetadataNavigationTest : LightJavaCodeInsightFixtureTestCase() {

  private val projectDescriptor = object : ProjectDescriptor(LanguageLevel.HIGHEST) {

    override fun getSourceRootType(): JpsModuleSourceRootType<*> = JavaSourceRootType.TEST_SOURCE

    override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk17()

  }

  override fun getProjectDescriptor(): LightProjectDescriptor = projectDescriptor

  override fun setUp() {
    super.setUp()
    ConfigLibraryUtil.configureKotlinRuntime(myFixture.module)
    myFixture.addClass("""
      package org.junit.platform.commons.annotation;
      public @interface Testable {}
     """.trimIndent())
    myFixture.addClass("""
                       package org.junit.jupiter.api;
                       @org.junit.platform.commons.annotation.Testable
                       public @interface Test {}
                       """.trimIndent())
    myFixture.addClass("package com.intellij.ui.components; public class JBList {}")
    myFixture.addClass("""
        package com.intellij.testFramework;
        
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;
        
        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.TYPE})
        public @interface TestDataPath {
          String value();
        }
        """.trimIndent())
    myFixture.addFileToProject("org/jetbrains/kotlin/test/TestMetadata.kt", """
      package org.jetbrains.kotlin.test

      @Retention(AnnotationRetention.RUNTIME)
      @Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
      annotation class TestMetadata(val value: String)
    """.trimIndent())
  }

  fun testNavigationByMetadata() {
    myFixture.configureByText("TestKotlin.kt", """
      package test

      import com.intellij.testFramework.TestDataPath
      import org.jetbrains.kotlin.test.TestMetadata
      import org.junit.jupiter.api.Test

      @TestDataPath("\${'$'}PROJECT_ROOT")
      @TestMetadata("testData/foo")
      class TestKotlin {
          @Test
          @TestMetadata("b")
          fun testFizzbuzz() {

          }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
    val gutters = myFixture.findAllGutters()
    val lineMarkerInfo = gutters.asSequence()
      .filterIsInstance<LineMarkerInfo.LineMarkerGutterIconRenderer<*>>()
      .map { it.lineMarkerInfo }
      .filter { it.element?.text == "testFizzbuzz" }
      .filter { it.createGutterRenderer()?.clickAction?.templateText == "Navigate to Test Data" }
      .single()

    val dataContext = MapDataContext().apply {
      put(Location.DATA_KEY, PsiLocation.fromPsiElement<PsiElement>(lineMarkerInfo.element))
    }
    val findTestDataFilesForTests = NavigateToTestDataAction.findTestDataFiles(
      dataContext, project, true).map { it.path }

    UsefulTestCase.assertSize(1, findTestDataFilesForTests)
    TestCase.assertTrue(findTestDataFilesForTests.single().endsWith("/testData/foo/b"))
  }

}