// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.inlays.declarative

import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayProviderPassInfo
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionPayload
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeHintsPreviewProvider
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPass
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPassFactory
import com.intellij.codeInsight.hints.declarative.impl.util.DeclarativeHintsDumpUtil
import com.intellij.codeInsight.hints.declarative.impl.views.InlayPresentationList
import com.intellij.codeInsight.hints.declarative.impl.views.TextInlayPresentationEntry
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestUtil
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File

/**
 * Declarative inlay hints provider checks for a JUnit 5 test.
 * This is the replacement of [DeclarativeInlayHintsProviderTestCase].
 *
 * Each check takes the [CodeInsightTestFixture] of the test.
 * Call a check from any thread. The pass collects the hints under a read action, and it applies them on the EDT.
 */
@TestOnly
object DeclarativeInlayHintsProviderTestUtil {
  /**
   * Removes the inlays from [expectedText] and configures the result as [fileName] in [fixture].
   * Then runs [doTestProviderWithConfigured].
   *
   * The file gets the exact name [fileName], as in the light temporary directory of a JUnit 3 test.
   * A provider can read the file name, and an expected text can hold it.
   * `configureByText` of a JUnit 5 fixture adds a unique suffix to the name.
   */
  @JvmStatic
  @JvmOverloads
  fun doTestProvider(
    fixture: CodeInsightTestFixture,
    fileName: String,
    expectedText: String,
    provider: InlayHintsProvider,
    enabledOptions: Map<String, Boolean> = emptyMap(),
    expectedFile: File? = null,
    verifyHintsPresence: Boolean = false,
    testMode: ProviderTestMode = ProviderTestMode.DETAILED,
    customToStringProvider: ((PsiElement) -> String)? = null,
  ) {
    val sourceText = InlayDumpUtil.removeInlays(expectedText)
    val file = fixture.addFileToProject(fileName, sourceText)
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    doTestProviderWithConfigured(fixture, sourceText, expectedText, provider, enabledOptions, expectedFile, verifyHintsPresence, testMode,
                                 customToStringProvider)
  }

  /**
   * Collects the hints of [provider] with [enabledOptions] in the file and the editor of [fixture].
   * Then compares the dump of the hints in [sourceText] with [expectedText].
   *
   * When [expectedFile] is not `null`, a mismatch throws a [FileComparisonFailedError] with the path of [expectedFile].
   * When [verifyHintsPresence] is `true`, also runs [InlayHintsProviderTestUtil.verifyHintsPresence].
   * [customToStringProvider] renders the target of a [PsiPointerInlayActionPayload] in [ProviderTestMode.DETAILED].
   */
  @JvmStatic
  @JvmOverloads
  fun doTestProviderWithConfigured(
    fixture: CodeInsightTestFixture,
    sourceText: String,
    expectedText: String,
    provider: InlayHintsProvider,
    enabledOptions: Map<String, Boolean>,
    expectedFile: File? = null,
    verifyHintsPresence: Boolean = false,
    testMode: ProviderTestMode = ProviderTestMode.DETAILED,
    customToStringProvider: ((PsiElement) -> String)? = null,
  ) {
    if (verifyHintsPresence) {
      InlayHintsProviderTestUtil.verifyHintsPresence(expectedText)
    }
    val file = fixture.file!!
    val editor = fixture.editor
    val providerInfo = InlayProviderPassInfo(provider, "provider.id", enabledOptions)
    val pass = ActionUtil.underModalProgress(fixture.project, "") {
      DeclarativeInlayHintsPass(file, editor, listOf(providerInfo), isPreview = false).also { it.setContext(file.codeInsightContext) }
    }
    applyPassAndCheckResult(fixture, pass, expectedFile, sourceText, expectedText, testMode, customToStringProvider)
  }

  /**
   * Configures the preview of [provider] for [language] in [fixture], collects its hints, and compares the dump with [expectedText].
   */
  @JvmStatic
  @JvmOverloads
  fun doTestPreview(
    fixture: CodeInsightTestFixture,
    @org.intellij.lang.annotations.Language("JAVA") expectedText: String,
    providerId: String,
    provider: InlayHintsProvider,
    language: Language,
    testMode: ProviderTestMode = ProviderTestMode.DETAILED,
    customToStringProvider: ((PsiElement) -> String)? = null,
  ) {
    val previewText = DeclarativeHintsPreviewProvider.getPreview(language, providerId, provider)
                      ?: error("Preview not found for provider: $providerId")
    val fileName = "preview." + (language.associatedFileType?.defaultExtension ?: error("language must have extension"))
    fixture.configureByText(fileName, InlayDumpUtil.removeInlays(previewText))

    val file = fixture.file
    val editor = fixture.editor
    val pass = ActionUtil.underModalProgress(fixture.project, "") {
      DeclarativeInlayHintsPassFactory.createPassForPreview(file, editor, provider, providerId, emptyMap(), false)
        .also { it.setContext(file.codeInsightContext) }
    }
    applyPassAndCheckResult(fixture, pass, null, previewText, expectedText, testMode, customToStringProvider)
  }

  private fun applyPassAndCheckResult(
    fixture: CodeInsightTestFixture,
    pass: DeclarativeInlayHintsPass,
    expectedFile: File?,
    previewText: String,
    expectedText: String,
    mode: ProviderTestMode,
    customToStringProvider: ((PsiElement) -> String)?,
  ) {
    ActionUtil.underModalProgress(fixture.project, "") {
      pass.doCollectInformation(EmptyProgressIndicator())
    }
    val dump = runInEdtAndGet {
      pass.applyInformationToEditor()
      DeclarativeHintsDumpUtil.dumpHints(previewText, editor = fixture.editor, renderer = { renderHint(it, mode, customToStringProvider) })
    }
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

  private fun renderHint(presentationList: InlayPresentationList, mode: ProviderTestMode, customToStringProvider: ((PsiElement) -> String)?): String {
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

  /** Specifies which information about individual hints is tested. */
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
    SIMPLE,
  }
}
