// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.inlays.declarative

import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * @deprecated Use [DeclarativeInlayHintsProviderTestUtil] in a JUnit 5 test.
 */
@Deprecated("Use DeclarativeInlayHintsProviderTestUtil in a JUnit 5 test.")
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
    DeclarativeInlayHintsProviderTestUtil.doTestProviderWithConfigured(myFixture, sourceText, expectedText, provider, enabledOptions,
                                                                       expectedFile, verifyHintsPresence, testMode.toUtilMode(),
                                                                       customToStringProvider)
  }

  @JvmOverloads
  fun doTestPreview(
    @org.intellij.lang.annotations.Language("JAVA") expectedText: String,
    providerId: String,
    provider: InlayHintsProvider,
    language: Language,
    testMode: ProviderTestMode = ProviderTestMode.DETAILED
  ) {
    DeclarativeInlayHintsProviderTestUtil.doTestPreview(myFixture, expectedText, providerId, provider, language, testMode.toUtilMode(),
                                                        customToStringProvider)
  }

  private fun ProviderTestMode.toUtilMode(): DeclarativeInlayHintsProviderTestUtil.ProviderTestMode =
    DeclarativeInlayHintsProviderTestUtil.ProviderTestMode.valueOf(name)

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