// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.CodeVisionInitializer
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionListData
import com.intellij.codeInsight.codeVision.ui.renderers.CodeVisionRenderer
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase
import com.intellij.testFramework.utils.inlays.InlayTestUtil
import java.util.regex.Pattern

abstract class CodeVisionTestCase : InlayHintsProviderTestCase() {

  override fun setUp() {
    Registry.get("editor.codeVision.new").setValue(true, testRootDisposable)
    super.setUp()
  }

  protected fun testProviders(expectedText: String, fileName: String, vararg enabledProviderIds: String) {
    // set enabled providers
    val settings = CodeVisionSettings.instance()
    val codeVisionHost = CodeVisionInitializer.getInstance(project).getCodeVisionHost()
    codeVisionHost.providers.forEach {
      settings.setProviderEnabled(it.id, enabledProviderIds.contains(it.id))
    }

    val sourceText = InlayTestUtil.inlayPattern.matcher(expectedText).replaceAll("")
    myFixture.configureByText(fileName, sourceText)

    val editor = myFixture.editor
    project.putUserData(CodeVisionHost.isCodeVisionTestKey, true)
    myFixture.doHighlighting()

    codeVisionHost.calculateCodeVisionSync(editor, testRootDisposable)

    val actualText = dumpCodeVisionHints(sourceText)
    assertEquals(expectedText, actualText)
  }

  private fun dumpCodeVisionHints(sourceText: String): String {
    return InlayTestUtil.dumpHintsInternal(sourceText, myFixture) { _, inlay ->
      inlay.getUserData(CodeVisionListData.KEY)?.visibleLens?.joinToString(prefix = "[", postfix = "]", separator = "   ") { it.longPresentation }!!
    }
  }
}