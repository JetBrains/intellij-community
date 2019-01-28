// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import com.intellij.codeInsight.daemon.impl.HighlightInfoType.ELEMENT_UNDER_CARET_READ
import com.intellij.codeInsight.daemon.impl.HighlightInfoType.ELEMENT_UNDER_CARET_WRITE
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory.doWithHighlightingEnabled
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.PlatformTestUtil.registerExtension
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase

class EditorConfigHighlightUsagesTest : LightPlatformCodeInsightFixtureTestCase() {
  override fun getTestDataPath() =
    "${PathManagerEx.getCommunityHomePath()}/plugins/editorconfig/testData/org/editorconfig/language/codeinsight/highlightUsages/"

  fun testParentlessOption() = doTest()
  fun testSubcaseParentOption() = doTest()
  fun testQualifiedKeyPart() = doTest()
  fun testDeclaration() = doTest()
  fun testReference() = doTest()
  fun testDeclarationWithoutReferences() = doTest()

  private fun doTest() {
    registerExtension(SeveritiesProvider.EP_NAME, SEVERITIES_PROVIDER, testRootDisposable)
    val name = getTestName(true)
    myFixture.configureByFile("${name}/.editorconfig")
    doWithHighlightingEnabled {
      ExpectedHighlightingData.expectedDuplicatedHighlighting {myFixture.checkHighlighting ()}
    }
  }

  companion object {
    private val SEVERITIES_PROVIDER = object : SeveritiesProvider() {
      override fun getSeveritiesHighlightInfoTypes() = listOf(ELEMENT_UNDER_CARET_READ, ELEMENT_UNDER_CARET_WRITE)
    }
  }
}
