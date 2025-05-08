// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight

import com.intellij.codeInsight.daemon.impl.HighlightInfoType.ELEMENT_UNDER_CARET_READ
import com.intellij.codeInsight.daemon.impl.HighlightInfoType.ELEMENT_UNDER_CARET_WRITE
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EditorConfigHighlightUsagesTest : BasePlatformTestCase() {
  override fun getTestDataPath() =
    "${PathManagerEx.getCommunityHomePath()}/plugins/editorconfig/testData/org/editorconfig/language/codeinsight/highlightUsages/"

  fun testParentlessOption() = doTest()
  fun testSubcaseParentOption() = doTest()
  fun testQualifiedKeyPart() = doTest()
  fun testDeclaration() = doTest()
  fun testReference() = doTest()
  fun testDeclarationWithoutReferences() = doTest()

  private fun doTest() {
    SeveritiesProvider.EP_NAME.point.registerExtension(SEVERITIES_PROVIDER, testRootDisposable)
    val name = getTestName(true)
    IdentifierHighlighterPassFactory.doWithIdentifierHighlightingEnabled (project, Runnable {
      myFixture.configureByFile("${name}/.editorconfig")
      myFixture.setReadEditorMarkupModel(true)
      myFixture.checkHighlighting()
    })
  }

  companion object {
    private val SEVERITIES_PROVIDER = object : SeveritiesProvider() {
      override fun getSeveritiesHighlightInfoTypes() = listOf(ELEMENT_UNDER_CARET_READ, ELEMENT_UNDER_CARET_WRITE)
    }
  }
}
