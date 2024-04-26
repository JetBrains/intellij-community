// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.dsl

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.dsl.builder.*
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class CommentsPanel : UISandboxPanel {

  override val title: String = "Comments"

  override fun createContent(disposable: Disposable): JComponent {
    var type = CommentComponentType.CHECKBOX
    val placeholder = JPanel(BorderLayout())

    fun applyType() {
      val builder = CommentPanelBuilder(type)
      placeholder.removeAll()
      placeholder.add(builder.build(), BorderLayout.CENTER)
    }

    val result = panel {
      row("Component type") {
        comboBox(CollectionComboBoxModel(CommentComponentType.entries))
          .onChanged {
            type = it.item ?: CommentComponentType.CHECKBOX
            applyType()
            placeholder.revalidate()
          }
      }
      row {
        cell(placeholder)
      }
    }

    applyType()
    return result
  }
}

@Suppress("DialogTitleCapitalization")
private class CommentPanelBuilder(val type: CommentComponentType) {

  fun build(): DialogPanel {
    return panel {
      for (rowLayout in RowLayout.entries) {
        val labelAligned = rowLayout == RowLayout.LABEL_ALIGNED

        group("rowLayout = $rowLayout") {
          row("With Label:") {
            customComponent("Long Component1")
              .comment("Component1 comment is aligned with Component1")
            customComponent("Component2")
            button("button") { }
          }.layout(rowLayout)
          row("With Long Label:") {
            customComponent("Component1")
            customComponent("Long Component2")
              .comment(
                if (labelAligned) "LABEL_ALIGNED: Component2 comment is aligned with Component1 (cell[1]), hard to fix, rare use case"
                else "Component2 comment is aligned with Component2")
            button("button") { }
          }.layout(rowLayout)
          row("With Very Long Label:") {
            customComponent("Component1")
            customComponent("Long Component2")
            button("button") { }
              .comment(if (labelAligned) "LABEL_ALIGNED: Button comment is aligned with Component1 (cell[1]), hard to fix, rare use case"
                       else "Button comment is aligned with button")
          }.layout(rowLayout)
          if (labelAligned) {
            row {
              label("LABEL_ALIGNED: in the next row only two first comments are shown")
            }
          }
          row {
            customComponent("Component1 extra width")
              .comment("Component1 comment")
            customComponent("Component2 extra width")
              .comment("Component2 comment<br>second line")
            customComponent("One More Long Component3")
              .comment("Component3 comment")
            button("button") { }
              .comment("Button comment")
          }.layout(rowLayout)
        }
      }
    }
  }

  private fun Row.customComponent(text: String): Cell<JComponent> {
    return when (type) {
      CommentComponentType.CHECKBOX -> checkBox(text)
      CommentComponentType.TEXT_FIELD -> textField().text(text)
      CommentComponentType.TEXT_FIELD_WITH_BROWSE_BUTTON -> textFieldWithBrowseButton().text(text)
    }
  }
}

private enum class CommentComponentType {
  CHECKBOX,
  TEXT_FIELD,
  TEXT_FIELD_WITH_BROWSE_BUTTON
}
