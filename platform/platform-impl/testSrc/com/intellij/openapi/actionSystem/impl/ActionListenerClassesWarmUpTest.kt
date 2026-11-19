// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.command.impl.SeparatedCommandListener
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class ActionListenerClassesWarmUpTest {
  @Test
  fun `the warm-up takes the platform listeners used by the first input`() {
    val actionListeners = lazyListenerClassLoaders(AnActionListener::class.java.name)
    val commandListeners = lazyListenerClassLoaders(SeparatedCommandListener::class.java.name)

    assertThat(WARM_UP_LISTENER_TOPICS).containsExactly(
      SeparatedCommandListener::class.java.name,
      AnActionListener::class.java.name,
    )
    assertThat(actionListeners).containsKey(ModifierKeyDoubleClickHandler.MyAnActionListener::class.java.name)
    assertThat(commandListeners).containsKey("com.intellij.openapi.command.impl.UndoCommandListener")
  }

  @Test
  fun `the warm-up loads the class of each listener`() {
    for (topicClassName in WARM_UP_LISTENER_TOPICS) {
      val classLoaders = lazyListenerClassLoaders(topicClassName)

      assertThat(classLoaders).isNotEmpty()
      assertThat(classLoaders.filterNot { [name, classLoader] ->
        loadListenerClass(name, classLoader)
      }).isEmpty()
    }
  }
}
