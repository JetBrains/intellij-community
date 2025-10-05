// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.inlays.declarative

import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayProviderPassInfo
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionPayload
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.codeInsight.hints.declarative.impl.*
import com.intellij.codeInsight.hints.declarative.impl.util.DeclarativeHintsDumpUtil
import com.intellij.codeInsight.hints.declarative.impl.views.InlayPresentationList
import com.intellij.codeInsight.hints.declarative.impl.views.TextInlayPresentationEntry
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase
import java.io.File

abstract class DeclarativeInlayHintsProviderTestCase : BasePlatformTestCase() {
  @JvmOverloads
  fun doTestProvider(
    fileName: String,
    expectedText: String,
    provider: InlayHintsProvider,
    enabledOptions: Map<String, Boolean> = emptyMap(),
    expectedFile: File? = null,
    verifyHintsPresence: Boolean = false,
    testMode: ProviderTestMode = ProviderTestMode.DETAILED,
  ) {
    val sourceText = InlayDumpUtil.removeInlays(expectedText)
    myFixture.configureByText(fileName, sourceText)
    doTestProviderWithConfigured(sourceText, expectedText, provider, enabledOptions, expectedFile, verifyHintsPresence, testMode)
  }

  fun doTestProviderWithConfigured(
    sourceText: String,
    expectedText: String,
    provider: InlayHintsProvider,
    enabledOptions: Map<String, Boolean>,
    expectedFile: File? = null,
    verifyHintsPresence: Boolean = false,
    testMode: ProviderTestMode = ProviderTestMode.DETAILED
  ) {
    if (verifyHintsPresence) {
      InlayHintsProviderTestCase.verifyHintsPresence(expectedText)
    }
    val file = myFixture.file!!
    val editor = myFixture.editor
    val providerInfo = InlayProviderPassInfo(provider, "provider.id", enabledOptions)
    val pass = ActionUtil.underModalProgress(project, "") {
      DeclarativeInlayHintsPass(file, editor, listOf(providerInfo), isPreview = false)
    }

    applyPassAndCheckResult(pass, expectedFile, sourceText, expectedText, testMode)
  }

  @JvmOverloads
  fun doTestPreview(
    @org.intellij.lang.annotations.Language("JAVA") expectedText: String,
    providerId: String,
    provider: InlayHintsProvider,
    language: Language,
    testMode: ProviderTestMode = ProviderTestMode.DETAILED
  ) {
    val previewText = DeclarativeHintsPreviewProvider.getPreview(language, providerId, provider) ?: error("Preview not found for provider: $providerId")
    val fileName = "preview." + (language.associatedFileType?.defaultExtension ?: error("language must have extension"))
    myFixture.configureByText(fileName, InlayDumpUtil.removeInlays(previewText))

    val pass = ActionUtil.underModalProgress(project, "") {
      DeclarativeInlayHintsPassFactory.createPassForPreview(myFixture.file, myFixture.editor, provider, providerId, emptyMap(), false)
    }
    applyPassAndCheckResult(pass, null, previewText, expectedText, testMode)
  }

  private fun applyPassAndCheckResult(
    pass: DeclarativeInlayHintsPass,
    expectedFile: File?,
    previewText: String,
    expectedText: String,
    mode: ProviderTestMode,
  ) {
    ActionUtil.underModalProgress(project, "") {
      pass.doCollectInformation(EmptyProgressIndicator())
    }
    pass.applyInformationToEditor()

    val dump = DeclarativeHintsDumpUtil.dumpHints(previewText, editor = myFixture.editor, renderer = { renderHint(it, mode) })
    val expectedTrim = expectedText.trim()
    val dumpTrim = dump.trim()
    if (expectedFile != null) {
      if (expectedTrim != dumpTrim) {
        throw FileComparisonFailedError("Text mismatch in the file ${expectedFile.absolutePath}", expectedTrim, dumpTrim, expectedFile.absolutePath)
      }
    }
    else {
      assertEquals(expectedTrim, dumpTrim)
    }
  }

  private fun renderHint(presentationList: InlayPresentationList, mode: ProviderTestMode): String {
    val entries = presentationList.getEntries()
    return when (mode) {
      ProviderTestMode.SIMPLE -> entries.joinToString(separator = "") { (it as TextInlayPresentationEntry).text }
      ProviderTestMode.DETAILED -> entries.joinToString(separator = "|") { entry ->
        val text = (entry as TextInlayPresentationEntry).text
        val actionData = entry.clickArea?.actionData
        val payload = actionData?.payload
        when (payload) {
          is PsiPointerInlayActionPayload -> (payload.pointer.element?.let { customToStringProvider?.invoke(it) } ?: "") + text
          is StringInlayActionPayload -> "[${payload.text}:${actionData.handlerId}]$text"
          else -> text
        }
      }
    }
  }

  var customToStringProvider: ((PsiElement) -> String)? = null

  /** Specifies which information about individual hints is tested.  */
  enum class ProviderTestMode {
    /**
     * Individual presentation text entries are separated by `|`.
     * Information about action payloads is also included.
     *
     * @see com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder.text
     * @see com.intellij.codeInsight.hints.declarative.InlayActionPayload
     */
    DETAILED,
    /**
     * Just the hint text as it is visible in the editor.
     */
    SIMPLE
  }
}