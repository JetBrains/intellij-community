// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class GroovyDuplicateNamesInTheFileTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_5_0)
  }

  private val highlighting by grHighlightingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "highlighting/duplicateNamesInTheFile")
  private val myFixture get() = highlighting.fixture

  @BeforeEach
  fun setUp() {
    myFixture.addFileToProject("foo/Bar.groovy",
                               """
                                 package foo
                                 
                                 class Bar {
                                     public static String Date = "01.01.2000"
                                 }
                               """.trimIndent())

    myFixture.addFileToProject("bar/Foo.groovy",
                               """
                                 package bar
                                 
                                 class Foo {
                                     public static String Date = "01.01.2007"
                                 }
                               """.trimIndent())
  }

  @Test
  fun testFqnImportAndFqnImport(): Unit = highlighting.doTest(false)

  @Test
  fun testFqnImportAndAlias(): Unit = highlighting.doTest(false)

  @Test
  fun testFqnImportAndOnDemandImport(): Unit = highlighting.doTest(false)

  @Test
  fun testFqnImportAndStaticImport(): Unit = highlighting.doTest(false)

  @Test
  fun testFqnImportAndStaticOnDemandImport(): Unit = doTest()

  @Test
  fun testFqnImportAndClassDefinition(): Unit = doTest()

  @Test
  fun testAliasAndAlias(): Unit = doTest()

  @Test
  fun testAliasAndOnDemandImport(): Unit = doTest()

  @Test
  fun testAliasAndStaticImport(): Unit = doTest()

  @Test
  fun testAliasAndStaticOnDemandImport(): Unit = doTest()

  @Test
  fun testAliasAndClassDefinition(): Unit = doTest()

  @Test
  fun testOnDemandImportAndOnDemandImport(): Unit = doTest()

  @Test
  fun testOnDemandImportAndStaticImport(): Unit = doTest()

  @Test
  fun testOnDemandImportAndStaticOnDemandImport(): Unit = doTest()

  @Test
  fun testOnDemandImportAndClassDefinition(): Unit = doTest()

  @Test
  fun testStaticImportAndStaticImport(): Unit = doTest()

  @Test
  fun testStaticImportAndStaticOnDemandImport(): Unit = doTest()

  @Test
  fun testStaticImportAndClassDefinition(): Unit = doTest()

  @Test
  fun testStaticOnDemandImportAndStaticOnDemandImport(): Unit = doTest()

  @Test
  fun testStaticOnDemandImportAndClassDefinition(): Unit = doTest()

  @Test
  fun testClassDefinitionAndClassDefinition(): Unit = doTest()

  @Test
  fun testOnlyFirstOccurenceIsNotHighlighted(): Unit = doTest()

  @Test
  fun testTypeParameterInDifferentMethodsIsNotHighlighted(): Unit = doTest()

  @Test
  fun testTypeParameterInClassAndMethodIsNotHighlighted(): Unit = doTest()

  private fun doTest() {
    highlighting.doTest(false)
  }
}