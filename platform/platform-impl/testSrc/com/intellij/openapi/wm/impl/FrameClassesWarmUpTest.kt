// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.EDT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class FrameClassesWarmUpTest {
  private val classLoader = FrameClassesWarmUp::class.java.classLoader

  @Test
  fun `every class of the warm-up exists`() {
    val missing = buildList {
      addAll(COMMON_FRAME_CLASS_NAMES)
      addAll(MAC_FRAME_CLASS_NAMES)
      addAll(OTHER_OS_FRAME_CLASS_NAMES)
    }.filter { name ->
      classLoader.getResource(name.replace('.', '/') + ".class") == null
    }
    assertThat(missing).isEmpty()
  }

  @Test
  fun `every class is listed once and every load-only class is listed`() {
    val all = buildList {
      addAll(COMMON_FRAME_CLASS_NAMES)
      addAll(MAC_FRAME_CLASS_NAMES)
      addAll(OTHER_OS_FRAME_CLASS_NAMES)
    }
    assertThat(all).doesNotHaveDuplicates()
    assertThat(all).containsAll(LOAD_ONLY_FRAME_CLASS_NAMES)
  }

  @Test
  fun `the classes that the warm-up initializes initialize off the EDT`() {
    assertThat(EDT.isCurrentThreadEdt()).isFalse()
    val failed = frameClassNamesOfCurrentOs().filter { it !in LOAD_ONLY_FRAME_CLASS_NAMES }.mapNotNull { name ->
      runCatching { Class.forName(name, true, classLoader) }.exceptionOrNull()?.let { "$name: $it" }
    }
    assertThat(failed).isEmpty()

    warmUpHtmlParser()
  }

  /**
   * The warm-up and the EDT initialize the same classes at the same time. The JVM holds one initialization lock per class.
   * Two threads that enter a cycle of the initialization graph at different classes thus wait for each other forever.
   * The test checks the classes of every OS, so that one agent also covers the macOS list.
   */
  @Test
  fun `the classes that the warm-up initializes reach no initialization cycle`() {
    val roots = buildList {
      addAll(COMMON_FRAME_CLASS_NAMES)
      addAll(MAC_FRAME_CLASS_NAMES)
    }.filter { it !in LOAD_ONLY_FRAME_CLASS_NAMES }.map { it.replace('.', '/') } + HTML_PARSER_CLASS_NAMES
    assertThat(ClassInitializationGraph(classLoader).findCycles(roots, initializedBefore = CSS_DEFAULTS_CLASS_NAMES)).isEmpty()
  }
}

/**
 * The classes that `configureCssUiDefaults` of the start-up initializes on the EDT before [initializeFrameClasses].
 * The `StyleSheet` constructor reaches `HTML` through the override `StyleSheet.addAttribute`, and the graph does not follow
 * an override, so the list names `HTML` too.
 */
private val CSS_DEFAULTS_CLASS_NAMES = listOf("com/intellij/ide/ui/html/GlobalStyleSheetHolderKt", "javax/swing/text/html/HTML")

/** The classes that [warmUpHtmlParser] initializes beyond the list. */
private val HTML_PARSER_CLASS_NAMES = listOf(
  "javax/swing/text/html/HTMLEditorKit", "javax/swing/text/html/StyleSheet", "javax/swing/text/html/HTMLDocument",
  "javax/swing/text/html/parser/ParserDelegator",
)
