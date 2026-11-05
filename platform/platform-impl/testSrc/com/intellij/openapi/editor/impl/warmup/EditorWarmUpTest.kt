// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.warmup

import com.intellij.openapi.editor.impl.EditorFactoryImpl
import com.intellij.openapi.wm.impl.ClassInitializationGraph
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class EditorWarmUpTest {
  private val classLoader = EditorFactoryImpl::class.java.classLoader

  @Test
  fun `every class of the editor list exists and is listed once`() {
    assertThat(EDITOR_CLASSES.initialize).isNotEmpty()
    assertThat(EDITOR_CLASSES.loadOnly).isNotEmpty()
    val all = EDITOR_CLASSES.initialize + EDITOR_CLASSES.loadOnly
    assertThat(all).doesNotHaveDuplicates()
    assertThat(all.filter { classLoader.getResource(internalName(it) + ".class") == null }).isEmpty()
  }

  /**
   * The warm-up and the EDT can initialize the same classes at the same time, for example for an editor text field.
   * Two threads that enter a cycle of the initialization graph at different classes wait for each other forever.
   */
  @Test
  fun `the classes that the warm-up initializes reach no initialization cycle and no load-only class`() {
    val graph = ClassInitializationGraph(classLoader)
    val roots = EDITOR_CLASSES.initialize.map(::internalName)
    val loadOnly = EDITOR_CLASSES.loadOnly.mapTo(HashSet(), ::internalName)

    assertThat(graph.findCycles(roots)).isEmpty()
    assertThat(graph.reachableFrom(roots).filter { it in loadOnly }).isEmpty()
  }
}

private fun internalName(className: String): String = className.replace('.', '/')
