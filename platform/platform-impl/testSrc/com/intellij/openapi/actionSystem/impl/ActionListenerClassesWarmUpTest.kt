// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class ActionListenerClassesWarmUpTest {
  @Test
  fun `the warm-up takes the action listeners of the platform`() {
    val classLoaders = lazyListenerClassLoaders(AnActionListener::class.java.name)

    assertThat(classLoaders).containsKey(ModifierKeyDoubleClickHandler.MyAnActionListener::class.java.name)
  }

  @Test
  fun `the warm-up loads the class of each listener`() {
    val classLoaders = lazyListenerClassLoaders(AnActionListener::class.java.name)

    assertThat(classLoaders).isNotEmpty()
    assertThat(classLoaders.filterNot { (name, classLoader) -> loadListenerClass(name, classLoader) }).isEmpty()
  }
}
