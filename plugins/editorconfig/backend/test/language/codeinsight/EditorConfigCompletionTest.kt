// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.editorconfig.EditorConfigRegistry
import org.editorconfig.language.assertIterableEquals
import org.editorconfig.language.services.EditorConfigOptionDescriptorManager
import org.editorconfig.language.services.impl.EditorConfigOptionDescriptorManagerImpl

class EditorConfigCompletionTest : BasePlatformTestCase() {
  override fun getTestDataPath() =
    "${PathManagerEx.getCommunityHomePath()}/plugins/editorconfig/testData/org/editorconfig/language/codeinsight/completion/"

  override fun setUp() {
    super.setUp()
    Registry.get(EditorConfigRegistry.EDITORCONFIG_DOTNET_SUPPORT_KEY).setValue(true)
    val descriptorManager = EditorConfigOptionDescriptorManager.getInstance(project) as EditorConfigOptionDescriptorManagerImpl
    descriptorManager.reloadDescriptors(project)
  }

  override fun tearDown() {
    try {
      Registry.get(EditorConfigRegistry.EDITORCONFIG_DOTNET_SUPPORT_KEY).resetToDefault()
      val descriptorManager = EditorConfigOptionDescriptorManager.getInstance(project) as EditorConfigOptionDescriptorManagerImpl
      descriptorManager.reloadDescriptors(project)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testComplexKeyFullTemplate() = doTest("dotnet_naming_rule, all required")
  fun testComplexKeyTemplate1() = doTest("dotnet_naming_rule", "dotnet_naming_style", "dotnet_naming_symbols")
  fun testComplexKeyTemplate2() = doInverseTest("my_rule")
  fun testComplexValue1() = doTest("all", "none", "accessors", "anonymous_methods", "lambdas")
  fun testComplexValue2() = doTest("anonymous_methods", "lambdas")
  fun testComplexValue3() = doInverseTest("all", "none", "accessors")
  fun testComplexValue4() = doExactTest("none", "silent", "refactoring", "suggestion", "warning", "error")
  fun testComplexValue5() = doInverseTest("none", "silent", "suggestion", "warning", "error")
  fun testComplexValue6() = doExactTest("my_symbols", "unset")
  fun testComplexValue7() = doExactTest("unset")
  fun testRootDeclaration1() = doExactTest("root = true", "[")
  fun testRootDeclaration2() = doInverseTest("root = true")
  fun testRootDeclaration3() = doExactTest("[")
  fun testRootDeclarationValue1() = doExactTest("true")
  fun testRootDeclarationValue2() = doInverseTest("true")
  fun testSimpleOptionKey1() = doTest("charset")
  fun testSimpleOptionKey2() = doInverseTest("indent_size", "indent_style")
  fun testSimpleOptionKey3() = doTest("charset")
  fun testSimpleOptionValue() = doExactTest("lf", "crlf", "cr", "unset")
  fun testCSharpOptionExistence() = doTestThatCompletionIsNotEmpty()
  fun testDotNetOptionExistence() = doTestThatCompletionIsNotEmpty()
  fun testReSharperOptionExistence() = doTestThatCompletionIsNotEmpty()

  fun doTest(vararg required: String) = with(myFixture) {
    val name = getTestName(true)
    configureByFile("$name/.editorconfig")
    assertTrue(required.all(completeBasic().map(LookupElement::getLookupString)::contains))
  }

  private fun doTestThatCompletionIsNotEmpty() = with(myFixture) {
    val name = getTestName(true)
    configureByFile("$name/.editorconfig")
    assertTrue(completeBasic().isNotEmpty())
  }

  private fun doInverseTest(vararg forbidden: String) = with(myFixture) {
    val name = getTestName(true)
    configureByFile("$name/.editorconfig")
    assertTrue(forbidden.none(completeBasic().map(LookupElement::getLookupString)::contains))
  }

  private fun doExactTest(vararg expected: String) = with(myFixture) {
    val name = getTestName(true)
    configureByFile("$name/.editorconfig")
    val actual = completeBasic().map(LookupElement::getLookupString)
    assertIterableEquals(expected.toList(), actual)
  }
}
