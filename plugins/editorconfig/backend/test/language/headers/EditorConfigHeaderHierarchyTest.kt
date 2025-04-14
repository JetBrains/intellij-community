// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.headers

import com.intellij.editorconfig.common.syntax.psi.EditorConfigPsiFile
import com.intellij.editorconfig.common.syntax.psi.EditorConfigSection
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.editorconfig.language.assertIterableEquals
import org.editorconfig.language.util.headers.EditorConfigHeaderOverrideSearcherBase
import org.editorconfig.language.util.headers.EditorConfigOverriddenHeaderSearcher
import org.editorconfig.language.util.headers.EditorConfigOverridingHeaderSearcher

class EditorConfigHeaderHierarchyTest : BasePlatformTestCase() {
  override fun getTestDataPath() =
    "${PathManagerEx.getCommunityHomePath()}/plugins/editorconfig/testData/org/editorconfig/language/headers/"

  fun testSimple() {
    testOverriding(0 to listOf(), 1 to listOf(0))
    testOverridden(0 to listOf(1), 1 to listOf())
    testPartiallyOverriding(0 to listOf(), 1 to listOf())
    testPartiallyOverridden(0 to listOf(), 1 to listOf())
  }

  fun testSupercaseBelow() {
    testOverriding(0 to listOf(), 1 to listOf())
    testOverridden(0 to listOf(), 1 to listOf())
    testPartiallyOverriding(0 to listOf(), 1 to listOf(0))
    testPartiallyOverridden(0 to listOf(1), 1 to listOf())
  }

  fun testComplexOverlap() {
    testOverriding(0 to listOf(), 1 to listOf())
    testOverridden(0 to listOf(), 1 to listOf())
    testPartiallyOverriding(0 to listOf(), 1 to listOf(0))
    testPartiallyOverridden(0 to listOf(1), 1 to listOf())
  }

  fun testBigFile() {
    testOverriding(0 to listOf(), 1 to listOf(0), 2 to listOf(0), 3 to listOf(0, 2), 4 to listOf(0))
    testOverridden(0 to listOf(1, 2, 3, 4), 1 to listOf(), 2 to listOf(3), 3 to listOf(), 4 to listOf())
    testPartiallyOverriding(0 to listOf(), 1 to listOf(), 2 to listOf(), 3 to listOf(), 4 to listOf())
    testPartiallyOverridden(0 to listOf(), 1 to listOf(), 2 to listOf(), 3 to listOf(), 4 to listOf())
  }

  private fun testOverriding(vararg pairs: Pair<Int, List<Int>>) = doStrictTest(mapOf(*pairs), EditorConfigOverridingHeaderSearcher())
  private fun testOverridden(vararg pairs: Pair<Int, List<Int>>) = doStrictTest(mapOf(*pairs), EditorConfigOverriddenHeaderSearcher())
  private fun testPartiallyOverriding(vararg pairs: Pair<Int, List<Int>>) =
    doPartialTest(mapOf(*pairs), EditorConfigOverridingHeaderSearcher())

  private fun testPartiallyOverridden(vararg pairs: Pair<Int, List<Int>>) =
    doPartialTest(mapOf(*pairs), EditorConfigOverriddenHeaderSearcher())

  private fun doStrictTest(referenceMap: Map<Int, List<Int>>, searcher: EditorConfigHeaderOverrideSearcherBase) {
    headers.forEachIndexed { index, header ->
      val matchingHeaders = searcher.findMatchingHeaders(header).mapNotNull { result ->
        result.takeUnless { it.isPartial }?.header
      }

      val expectedTargets = referenceMap[index]?.map(headers::get) ?: throw AssertionError()
      assertIterableEquals(expectedTargets, matchingHeaders)
    }
  }

  private fun doPartialTest(referenceMap: Map<Int, List<Int>>, searcher: EditorConfigHeaderOverrideSearcherBase) {
    headers.forEachIndexed { index, header ->
      val matchingHeaders = searcher.findMatchingHeaders(header).mapNotNull { result ->
        result.takeIf { it.isPartial }?.header
      }

      val expectedTargets = referenceMap[index]?.map(headers::get) ?: throw AssertionError()
      assertIterableEquals(expectedTargets, matchingHeaders)
    }
  }

  private val headers by lazy { editorConfigFile.sections.map(EditorConfigSection::getHeader) }
  private val editorConfigFile by lazy { myFixture.configureByFile("${getTestName(true)}/.editorconfig") as EditorConfigPsiFile }
}
