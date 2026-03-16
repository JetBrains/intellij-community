// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.util.TestUtils

class GroovyDuplicateNamesInTheFileTest : GrHighlightingTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_5_0
  }

  override fun getBasePath(): String {
    return TestUtils.getTestDataPath() + "highlighting/duplicateNamesInTheFile"
  }

  override fun setUp() {
    super.setUp()
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

  fun testFqnImportAndFqnImport() = doTest(false)

  fun testFqnImportAndAlias() = doTest(false)

  fun testFqnImportAndOnDemandImport() = doTest(false)

  fun testFqnImportAndStaticImport() = doTest(false)

  fun testFqnImportAndStaticOnDemandImport() = doTest()

  fun testFqnImportAndClassDefinition() = doTest()

  fun testAliasAndAlias() = doTest()

  fun testAliasAndOnDemandImport() = doTest()

  fun testAliasAndStaticImport() = doTest()

  fun testAliasAndStaticOnDemandImport() = doTest()

  fun testAliasAndClassDefinition() = doTest()

  fun testOnDemandImportAndOnDemandImport() = doTest()

  fun testOnDemandImportAndStaticImport() = doTest()

  fun testOnDemandImportAndStaticOnDemandImport() = doTest()

  fun testOnDemandImportAndClassDefinition() = doTest()

  fun testStaticImportAndStaticImport() = doTest()

  fun testStaticImportAndStaticOnDemandImport() = doTest()

  fun testStaticImportAndClassDefinition() = doTest()

  fun testStaticOnDemandImportAndStaticOnDemandImport() = doTest()

  fun testStaticOnDemandImportAndClassDefinition() = doTest()

  fun testClassDefinitionAndClassDefinition() = doTest()

  fun testOnlyFirstOccurenceIsNotHighlighted() = doTest()

  fun testTypeParameterInDifferentMethodsIsNotHighlighted() = doTest()

  fun testTypeParameterInClassAndMethodIsNotHighlighted() = doTest()


  private fun doTest() {
    doTest(false)
  }
}