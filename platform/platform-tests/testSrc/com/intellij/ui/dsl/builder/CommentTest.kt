// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import org.junit.Test
import javax.swing.JTextField
import kotlin.test.assertEquals

class CommentTest {

  @Test
  fun testAccessibleDescription() {
    assertEquals(createTextField().accessibleContext.accessibleDescription, null)
    assertEquals(createTextField(commentRight = "").accessibleContext.accessibleDescription, null)
    assertEquals(createTextField(comment = "").accessibleContext.accessibleDescription, null)
    assertEquals(createTextField(commentRight = "    \n    \t").accessibleContext.accessibleDescription, null)
    assertEquals(createTextField(comment = "    \n    \t").accessibleContext.accessibleDescription, null)
    assertEquals(createTextField("    Some right text  ", "    Some text  ").accessibleContext.accessibleDescription, "Some right text\nSome text")
    assertEquals(createTextField("    <a>Some link</a> and text ", "<a>Some link</a>").accessibleContext.accessibleDescription, "Some link and text\nSome link")
  }

  @Test
  fun testCustomAccessibleDescription() {
    val contextBeforeCommentRight = createTextField {
      accessibleDescription("Custom description")
      commentRight("Some comment")
    }.component
    assertEquals(contextBeforeCommentRight.accessibleContext.accessibleDescription, "Custom description")
    val contextBeforeComment = createTextField {
      accessibleDescription("Custom description")
      comment("Some comment")
    }.component
    assertEquals(contextBeforeComment.accessibleContext.accessibleDescription, "Custom description")

    val contextAfterCommentRight = createTextField {
      commentRight("Some comment")
      accessibleDescription("Custom description")
    }.component
    assertEquals(contextAfterCommentRight.accessibleContext.accessibleDescription, "Custom description")
    val contextAfterComment = createTextField {
      comment("Some comment")
      accessibleDescription("Custom description")
    }.component
    assertEquals(contextAfterComment.accessibleContext.accessibleDescription, "Custom description")
  }

  @Test
  fun testUpdatingCommentAccessibleDescription() {
    val cell = createTextField {
      commentRight("1")
      comment("2")
    }
    assertEquals(cell.component.accessibleContext.accessibleDescription, "1\n2")

    cell.comment!!.text = "3"
    assertEquals(cell.component.accessibleContext.accessibleDescription, "1\n3")

    cell.commentRight!!.text = ""
    assertEquals(cell.component.accessibleContext.accessibleDescription, "3")

    cell.accessibleDescription("4")
    assertEquals(cell.component.accessibleContext.accessibleDescription, "4")
    cell.comment!!.text = "5"
    assertEquals(cell.component.accessibleContext.accessibleDescription, "4")
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