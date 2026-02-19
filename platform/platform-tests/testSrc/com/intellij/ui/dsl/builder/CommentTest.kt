// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import javax.swing.JTextField

class CommentTest {

  @Test
  fun testAccessibleDescription() {
    assertThat(createTextField().accessibleContext.accessibleDescription).isEqualTo(null)
    assertThat(createTextField(commentRight = "").accessibleContext.accessibleDescription).isEqualTo(null)
    assertThat(createTextField(comment = "").accessibleContext.accessibleDescription).isEqualTo(null)
    assertThat(createTextField(commentRight = "    \n    \t").accessibleContext.accessibleDescription).isEqualTo(null)
    assertThat(createTextField(comment = "    \n    \t").accessibleContext.accessibleDescription).isEqualTo(null)
    assertThat(createTextField("    Some right text  ", "    Some text  ").accessibleContext.accessibleDescription)
      .isEqualTo("Some right text\nSome text")
    assertThat(createTextField("    <a>Some link</a> and text ", "<a>Some link</a>").accessibleContext.accessibleDescription)
      .isEqualTo("Some link and text\nSome link")
  }

  @Test
  fun testCustomAccessibleDescription() {
    val contextBeforeCommentRight = createTextField {
      accessibleDescription("Custom description")
      commentRight("Some comment")
    }.component
    assertThat(contextBeforeCommentRight.accessibleContext.accessibleDescription).isEqualTo("Custom description")
    val contextBeforeComment = createTextField {
      accessibleDescription("Custom description")
      comment("Some comment")
    }.component
    assertThat(contextBeforeComment.accessibleContext.accessibleDescription).isEqualTo("Custom description")

    val contextAfterCommentRight = createTextField {
      commentRight("Some comment")
      accessibleDescription("Custom description")
    }.component
    assertThat(contextAfterCommentRight.accessibleContext.accessibleDescription).isEqualTo("Custom description")
    val contextAfterComment = createTextField {
      comment("Some comment")
      accessibleDescription("Custom description")
    }.component
    assertThat(contextAfterComment.accessibleContext.accessibleDescription).isEqualTo("Custom description")
  }

  @Test
  fun testUpdatingCommentAccessibleDescription() {
    val cell = createTextField {
      commentRight("1")
      comment("2")
    }
    assertThat(cell.component.accessibleContext.accessibleDescription).isEqualTo("1\n2")

    cell.comment!!.text = "3"
    assertThat(cell.component.accessibleContext.accessibleDescription).isEqualTo("1\n3")

    cell.commentRight!!.text = ""
    assertThat(cell.component.accessibleContext.accessibleDescription).isEqualTo("3")

    cell.accessibleDescription("4")
    assertThat(cell.component.accessibleContext.accessibleDescription).isEqualTo("4")
    cell.comment!!.text = "5"
    assertThat(cell.component.accessibleContext.accessibleDescription).isEqualTo("4")
  }

  private fun createTextField(commentRight: String? = null, comment: String? = null): JTextField {
    return createTextField {
      commentRight(commentRight)
      comment(comment)
    }.component
  }

  private fun createTextField(init: Cell<JTextField>.() -> Unit): Cell<JTextField> {
    lateinit var result: Cell<JTextField>
    panel {
      row {
        result = textField()
        result.init()
      }
    }
    return result
  }
}