// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.EditorHostedComponent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JComponent

internal class EditorInputFocusTest {
  @Test
  fun `the content component itself owns the input focus`() {
    val content = TestComponent()

    assertThat(isInputFocusOwner(content, content)).isTrue()
  }

  @Test
  fun `a plain child of the content component owns the input focus`() {
    val content = TestComponent()
    val child = TestComponent()
    content.add(child)

    assertThat(isInputFocusOwner(child, content)).isTrue()
  }

  @Test
  fun `a hosted component takes the input focus from the content component`() {
    val content = TestComponent()
    val hosted = TestHostedComponent(isInputFocusOwner = true)
    val child = TestComponent()
    content.add(hosted)
    hosted.add(child)

    assertThat(isInputFocusOwner(child, content)).isFalse()
    assertThat(isInputFocusOwner(hosted, content)).isFalse()
  }

  @Test
  fun `a hosted component that owns no input focus leaves it to the content component`() {
    val content = TestComponent()
    val hosted = TestHostedComponent(isInputFocusOwner = false)
    val child = TestComponent()
    content.add(hosted)
    hosted.add(child)

    assertThat(isInputFocusOwner(child, content)).isTrue()
  }

  @Test
  fun `a nested editor holds the input focus alone`() {
    val outerContent = TestComponent()
    val hosted = TestHostedComponent(isInputFocusOwner = true)
    val innerContent = TestComponent()
    outerContent.add(hosted)
    hosted.add(innerContent)

    assertThat(isInputFocusOwner(innerContent, innerContent)).isTrue()
    assertThat(isInputFocusOwner(innerContent, outerContent)).isFalse()
  }

  @Test
  fun `a component beside the content component owns no input focus`() {
    val root = TestComponent()
    val content = TestComponent()
    val other = TestComponent()
    root.add(content)
    root.add(other)

    assertThat(isInputFocusOwner(other, content)).isFalse()
  }

  @Test
  fun `a detached content component owns no input focus`() {
    val root = TestComponent()
    val child = TestComponent()
    root.add(child)

    assertThat(isInputFocusOwner(child, TestComponent())).isFalse()
  }

  @Test
  fun `no focus owner means no input focus owner`() {
    assertThat(isInputFocusOwner(null, TestComponent())).isFalse()
  }

  private class TestComponent : JComponent()

  private class TestHostedComponent(
    override val isInputFocusOwner: Boolean,
  ) : JComponent(), EditorHostedComponent
}
