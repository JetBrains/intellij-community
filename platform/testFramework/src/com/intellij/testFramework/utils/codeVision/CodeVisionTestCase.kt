// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.CodeVisionInitializer
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionListData
import com.intellij.codeInsight.codeVision.ui.renderers.CodeVisionInlayRenderer
import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase

abstract class CodeVisionTestCase : InlayHintsProviderTestCase() {

  companion object {
    const val AUTHOR_HINT: String = "John Smith +2"
  }

  protected open val onlyCodeVisionHintsAllowed: Boolean
    get() = false


  override fun setUp() {
    Registry.get("editor.codeVision.new").setValue(true, testRootDisposable)
    TestModeFlags.set(CodeVisionHost.isCodeVisionTestKey, true, testRootDisposable)
    super.setUp()
  }

  override fun tearDown() {
    try {
      val settings = CodeVisionSettings.getInstance()
      val codeVisionHost = CodeVisionInitializer.getInstance(project).getCodeVisionHost()
      codeVisionHost.providers.forEach {
        settings.setProviderEnabled(it.groupId, true)
      }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  protected fun testProviders(expectedText: String, fileName: String, vararg enabledProviderGroupIds: String) {
    // set enabled providers
    val settings = CodeVisionSettings.getInstance()
    val codeVisionHost = CodeVisionInitializer.getInstance(project).getCodeVisionHost()
    codeVisionHost.providers.map { it.groupId }.toSet().forEach {
      settings.setProviderEnabled(it, enabledProviderGroupIds.contains(it))
    }

    val sourceText = InlayDumpUtil.removeHints(expectedText)
    myFixture.configureByText(fileName, sourceText)

    val editor = myFixture.editor
    project.putUserData(CodeVisionHost.isCodeVisionTestKey, true)
    codeVisionHost.providers.forEach {
      if (it.id == "vcs.code.vision" && enabledProviderGroupIds.contains(it.groupId)) {
        it.preparePreview(myFixture.editor, myFixture.file)
      }
    }
    myFixture.doHighlighting()

    codeVisionHost.calculateCodeVisionSync(editor, testRootDisposable)

    val actualText = dumpCodeVisionHints(sourceText)
    assertEquals(expectedText, actualText)
  }

  private fun dumpCodeVisionHints(sourceText: String): String {
    return InlayDumpUtil.dumpHintsInternal(
      sourceText, myFixture.editor,
      filter = {
        val rendererSupported = it.renderer is CodeVisionInlayRenderer
        if (onlyCodeVisionHintsAllowed && !rendererSupported) error("renderer not supported")
        rendererSupported
      },
      renderer = { _, inlay, _ ->
        inlay.getUserData(CodeVisionListData.KEY)!!.visibleLens.joinToString(prefix = "[", postfix = "]", separator = "   ") { it.longPresentation }
      })
  }
}