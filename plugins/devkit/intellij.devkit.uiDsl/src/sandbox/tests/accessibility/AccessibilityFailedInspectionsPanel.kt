// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.tests.accessibility

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.icons.AllIcons
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import java.awt.Graphics
import javax.accessibility.Accessible
import javax.accessibility.AccessibleAction
import javax.accessibility.AccessibleEditableText
import javax.accessibility.AccessibleRole
import javax.accessibility.AccessibleText
import javax.accessibility.AccessibleValue
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JProgressBar
import javax.swing.JTextField

@Suppress("DialogTitleCapitalization")
internal class AccessibilityFailedInspectionsPanel : UISandboxPanel {
  override val title: String = DevkitUiDslBundle.message("sandbox.failed.inspections")
  override fun createContent(disposable: Disposable): JComponent = panel {
    row {
      text(DevkitUiDslBundle.message("sandbox.label.this.page.shows.examples.in.ui.inspector"))
    }
    group(DevkitUiDslBundle.message("sandbox.border.title.accessible.state.set.contains.focusable")) {
      twoColumnsRow(
        {
          button(DevkitUiDslBundle.message("sandbox.button.not.focusable.button")) {}.applyToComponent {
            isFocusable = false
          }
        }, {
          button(DevkitUiDslBundle.message("sandbox.button.normal.button2")) {}
        })
    }

    group(DevkitUiDslBundle.message("sandbox.border.title.accessible.action.value.are.not.null")) {
      twoColumnsRow(
        {
          cell(object : JCheckBox(DevkitUiDslBundle.message("sandbox.broken.checkbox")) {
            override fun getAccessibleContext() = object : AccessibleJCheckBox() {
              override fun getAccessibleAction(): AccessibleAction? = null
              override fun getAccessibleValue(): AccessibleValue? = null
            }
          })
        }, {
          checkBox(DevkitUiDslBundle.message("sandbox.checkbox.normal.checkbox"))
        })
    }

    group(DevkitUiDslBundle.message("sandbox.border.title.accessible.name.description.are.not.equal")) {
      twoColumnsRow(
        {
          button(DevkitUiDslBundle.message("sandbox.button.broken.button")) {}.applyToComponent {
            accessibleContext.accessibleDescription = DevkitUiDslBundle.message("sandbox.button")
            accessibleContext.accessibleName = DevkitUiDslBundle.message("sandbox.button")
          }
        }, {
          button(DevkitUiDslBundle.message("sandbox.button.normal.button")) {}
        })
    }

    group(DevkitUiDslBundle.message("sandbox.border.title.accessible.value.not.null")) {
      twoColumnsRow(
        {
          cell(object : JProgressBar() {
            override fun getAccessibleContext() = object : AccessibleJProgressBar() {
              override fun getAccessibleValue(): AccessibleValue? = null
            }
          }).label(DevkitUiDslBundle.message("sandbox.label.broken.progress.bar"), LabelPosition.TOP)
        }, {
          cell(JProgressBar())
            .label(DevkitUiDslBundle.message("sandbox.label.normal.progress.bar"), LabelPosition.TOP)
        })
    }

    group(DevkitUiDslBundle.message("sandbox.border.title.multiple.failed.inspections")) {
      twoColumnsRow(
        {
          cell(object : JTextField() {
            override fun getAccessibleContext() = object : AccessibleJTextField() {
              override fun getAccessibleText(): AccessibleText? = null
              override fun getAccessibleEditableText(): AccessibleEditableText? = null
              override fun getAccessibleName(): String? = null
            }

          }).label(DevkitUiDslBundle.message("sandbox.label.broken.text.field"), LabelPosition.TOP)
        }, {
          textField().label(DevkitUiDslBundle.message("sandbox.label.normal.text.field"), LabelPosition.TOP)
        })
    }

    group(DevkitUiDslBundle.message("sandbox.border.title.component.with.icon.has.non.default.accessible.name")) {
      twoColumnsRow(
        {
          cell(JLabel(DevkitUiDslBundle.message("sandbox.some.info")).apply {
            icon = AllIcons.General.Error
          }).label(DevkitUiDslBundle.message("sandbox.label.jlabel.with.default.accessible.name"), LabelPosition.TOP)
        }, {
          cell(JLabel(DevkitUiDslBundle.message("sandbox.some.info")).apply {
            icon = AllIcons.General.Error
            accessibleContext.accessibleName = DevkitUiDslBundle.message("sandbox.error.some.info")
          }).label(DevkitUiDslBundle.message("sandbox.label.jlabel.with.custom.accessible.name"), LabelPosition.TOP)
        })

      twoColumnsRow(
        {
          cell(SimpleColoredComponent().apply {
            icon = AllIcons.General.Error
            append(DevkitUiDslBundle.message("sandbox.some.info"))
          }).label(DevkitUiDslBundle.message("sandbox.label.simplecoloredcomponent.with.default.name"), LabelPosition.TOP)
        }, {
          cell(object : SimpleColoredComponent() {
            override fun getAccessibleContext() = object : AccessibleSimpleColoredComponent() {
              override fun getAccessibleName(): String = DevkitUiDslBundle.message("sandbox.error.some.info")
            }
          }.apply {
            icon = AllIcons.General.Error
            append(DevkitUiDslBundle.message("sandbox.some.info"))
          }).label(DevkitUiDslBundle.message("sandbox.label.simplecoloredcomponent.with.custom.name"), LabelPosition.TOP)
        })
    }

    group(DevkitUiDslBundle.message("sandbox.border.title.implements.accessible")) {
      twoColumnsRow(
        {
          cell(object : JComponent() {
            override fun paintComponent(g: Graphics) {
              super.paintComponent(g)
              g.color = background
              g.fillRect(0, 0, width, height)
            }
          }.apply {
            preferredSize = Dimension(100, 50)
            background = JBColor.RED
          }).label(DevkitUiDslBundle.message("sandbox.label.not.accessible.component"), LabelPosition.TOP)
        }, {
          cell(object : JComponent(), Accessible {
            override fun getAccessibleContext() = object : AccessibleJComponent() {
              override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PANEL
            }

            override fun paintComponent(g: Graphics) {
              super.paintComponent(g)
              g.color = background
              g.fillRect(0, 0, width, height)
            }
          }.apply {
            preferredSize = Dimension(100, 50)
            background = JBColor.GREEN
          }).label(DevkitUiDslBundle.message("sandbox.label.accessible.component"), LabelPosition.TOP)
        })
    }
  }
}
