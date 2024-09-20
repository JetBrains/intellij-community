// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.inlays

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.containers.isEmpty

abstract class InlayHintsProviderTestCase : BasePlatformTestCase() {

  companion object {
    private const val NO_HINTS_PREFIX = "// NO_HINTS"

    fun verifyHintsPresence(expectedText: String) {
      val expectedHintPresence =
        if (expectedText.lineSequence().any { it.startsWith(NO_HINTS_PREFIX) }) HintPresence.NO_HINTS else HintPresence.SOME_HINTS
      val actualHintPresence =
        if (InlayDumpUtil.inlayPattern.matcher(expectedText).results().isEmpty()) HintPresence.NO_HINTS else HintPresence.SOME_HINTS
      assertEquals("Hint presence should match the use of the $NO_HINTS_PREFIX directive.", expectedHintPresence, actualHintPresence)
    }

    private enum class HintPresence {
      NO_HINTS,
      SOME_HINTS
    }
  }

  @JvmOverloads
  fun <T : Any> doTestProvider(fileName: String,
                               expectedText: String,
                               provider: InlayHintsProvider<T>,
                               settings: T = provider.createSettings(),
                               verifyHintPresence: Boolean = false) {
    val sourceText = InlayDumpUtil.removeHints(expectedText)
    myFixture.configureByText(fileName, sourceText)
    val actualText = dumpInlayHints(sourceText, provider, settings)
    assertEquals(expectedText, actualText)

    if(verifyHintPresence) {
      verifyHintsPresence(expectedText)
    }
  }

  protected fun <T : Any> dumpInlayHints(sourceText: String,
                                         provider: InlayHintsProvider<T>,
                                         settings: T = provider.createSettings()): String {
    val file = myFixture.file!!
    val editor = myFixture.editor
    val sink = InlayHintsSinkImpl(editor)
    val collector = provider.getCollectorFor(file, editor, settings, sink) ?: error("Collector is expected")
    val collectorWithSettings = CollectorWithSettings(collector, provider.key, file.language, sink)
    collectorWithSettings.collectTraversingAndApply(editor, file, true)
    return InlayDumpUtil.dumpHintsInternal(sourceText, editor = myFixture.editor, renderer = { renderer, _ ->
      if (renderer !is PresentationRenderer && renderer !is LinearOrderInlayRenderer<*>) error("renderer not supported")
      renderer.toString()
    })
  }
}