// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.inlays.declarative

import com.intellij.codeInsight.hints.declarative.impl.DeclarativeHintsPreviewProvider
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayProviderPassInfo
import com.intellij.codeInsight.hints.declarative.impl.*
import com.intellij.lang.Language
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.inlays.InlayTestUtil

abstract class DeclarativeInlayHintsProviderTestCase : BasePlatformTestCase() {
  fun doTestProvider(fileName: String, expectedText: String, provider: InlayHintsProvider, enabledOptions: Map<String, Boolean> = emptyMap()) {
    val sourceText = InlayTestUtil.inlayPattern.matcher(expectedText).replaceAll("")
    myFixture.configureByText(fileName, sourceText)
    val file = myFixture.file!!
    val editor = myFixture.editor
    val providerInfo = InlayProviderPassInfo(provider, "provider.id", enabledOptions)
    val pass = DeclarativeInlayHintsPass(file, editor, listOf(providerInfo), isPreview = false)
    applyPassAndCheckResult(pass, sourceText, expectedText)
  }

  fun doTestPreview(expectedText: String, providerId: String, provider: InlayHintsProvider, language: Language) {
    val previewText = DeclarativeHintsPreviewProvider.getPreview(language, providerId, provider) ?: error("Preview not found for provider: $providerId")
    val fileName = "preview." + (language.associatedFileType?.defaultExtension ?: error("language must have extension"))
    myFixture.configureByText(fileName, previewText)

    val pass = DeclarativeInlayHintsPassFactory.createPassForPreview(myFixture.file, myFixture.editor, provider, providerId,
                                                                     emptyMap(), false)
    applyPassAndCheckResult(pass, previewText, expectedText)
  }

  fun doTestOptionPreview(expectedText: String, providerId: String, provider: InlayHintsProvider, language: Language, optionId: String) {
    val previewText = DeclarativeHintsPreviewProvider.getOptionPreview(language, providerId, optionId, provider) ?: error("Preview not found for provider: $providerId and option $optionId")
    val fileName = "preview." + (language.associatedFileType?.defaultExtension ?: error("language must have extension"))
    myFixture.configureByText(fileName, previewText)

    val options = mapOf(optionId to true)
    val pass = DeclarativeInlayHintsPassFactory.createPassForPreview(myFixture.file, myFixture.editor, provider, providerId,
                                                                     options, false)
    applyPassAndCheckResult(pass, previewText, expectedText)
  }

  private fun applyPassAndCheckResult(pass: DeclarativeInlayHintsPass,
                        previewText: String,
                        expectedText: String) {
    pass.doCollectInformation(EmptyProgressIndicator())
    pass.applyInformationToEditor()

    val dump = InlayTestUtil.dumpHintsInternal(previewText, myFixture) { renderer, _ ->
      renderer as DeclarativeInlayRenderer
      renderer.presentationList.getEntries().joinToString(separator = "|") { entry -> (entry as TextInlayPresentationEntry).text }
    }
    assertEquals(expectedText.trim(), dump.trim())
  }
}