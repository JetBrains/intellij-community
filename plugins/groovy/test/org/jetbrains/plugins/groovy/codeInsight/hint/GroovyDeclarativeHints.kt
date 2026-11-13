// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GroovyDeclarativeHints")

package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayProviderPassInfo
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPass
import com.intellij.codeInsight.hints.declarative.impl.util.DeclarativeHintsDumpUtil
import com.intellij.codeInsight.hints.declarative.impl.views.InlayPresentationList
import com.intellij.codeInsight.hints.declarative.impl.views.TextInlayPresentationEntry
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.util.configureByExactName
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Configures [fileName] with [expectedText] without its hints, runs [provider], and compares the hints with [expectedText].
 * It replaces `doTestProvider` of the JUnit 3 base `DeclarativeInlayHintsProviderTestCase` in its detailed mode.
 */
@TestOnly
fun doTestDeclarativeProvider(fixture: JavaCodeInsightTestFixture, fileName: String, expectedText: String, provider: InlayHintsProvider) {
  val sourceText = InlayDumpUtil.removeInlays(expectedText)
  configureByExactName(fixture, fileName, sourceText)
  timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val file = fixture.file
      val editor = fixture.editor
      val providerInfo = InlayProviderPassInfo(provider, "provider.id", emptyMap())
      val pass = ActionUtil.underModalProgress(fixture.project, "") {
        DeclarativeInlayHintsPass(file, editor, listOf(providerInfo), isPreview = false)
      }
      pass.setContext(file.codeInsightContext)
      ActionUtil.underModalProgress(fixture.project, "") {
        pass.doCollectInformation(EmptyProgressIndicator())
      }
      pass.applyInformationToEditor()

      val dump = DeclarativeHintsDumpUtil.dumpHints(sourceText, editor = editor, renderer = ::renderHint)
      assertEquals(expectedText.trim(), dump.trim())
    }
  }
}

private fun renderHint(presentationList: InlayPresentationList): String {
  return presentationList.getEntries().joinToString(separator = "|") { entry ->
    val text = (entry as TextInlayPresentationEntry).text
    val actionData = entry.clickArea?.actionData
    when (val payload = actionData?.payload) {
      is StringInlayActionPayload -> "[${payload.text}:${actionData.handlerId}]$text"
      else -> text
    }
  }
}
