// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import org.junit.Test
import javax.swing.JTextField
import kotlin.test.assertEquals

class CommentTest {

  @Test
  fun testAccessibleDescription() {
    val nullComment = createTextField(null)
    assertEquals(nullComment.accessibleContext.accessibleDescription, null)

    val emptyComment = createTextField("")
    assertEquals(emptyComment.accessibleContext.accessibleDescription, "")

    val blankComment = createTextField("    \n    \t")
    assertEquals(blankComment.accessibleContext.accessibleDescription, "")

    val textComment = createTextField("    Some text  ")
    assertEquals(textComment.accessibleContext.accessibleDescription, "Some text")

    val htmlComment = createTextField("    <a>Some link</a> and text ")
    assertEquals(htmlComment.accessibleContext.accessibleDescription, "Some link and text")
  }

  @Test
  fun testCustomAccessibleDescription() {
    val contextBeforeComment = createTextField {
      accessibleDescription("Custom description")
      comment("Some comment")
    }.component
    assertEquals(contextBeforeComment.accessibleContext.accessibleDescription, "Custom description")

    val contextAfterComment = createTextField {
      comment("Some comment")
      accessibleDescription("Custom description")
    }.component
    assertEquals(contextAfterComment.accessibleContext.accessibleDescription, "Custom description")
  }

  @Test
  fun testUpdatingCommentAccessibleDescription() {
    val cell = createTextField {
      comment("1")
    }
    assertEquals(cell.component.accessibleContext.accessibleDescription, "1")

    cell.comment!!.text = "2"
    assertEquals(cell.component.accessibleContext.accessibleDescription, "2")

    cell.accessibleDescription("3")
    cell.comment!!.text = "4"
    assertEquals(cell.component.accessibleContext.accessibleDescription, "3")
  }

  private fun createTextField(comment: String?): JTextField {
    return createTextField {
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