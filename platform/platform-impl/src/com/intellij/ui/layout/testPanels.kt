// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")
package com.intellij.ui.layout

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.*
import java.awt.GridLayout
import javax.swing.*

@Deprecated(message = "New UI DSL is implemented, see com.intellij.ui.dsl. Whole file should be removed")
fun labelRowShouldNotGrow(): JPanel {
  return panel {
    row("Create Android module") { CheckBox("FooBar module name foo")() }
    row("Android module name:") { JTextField("input")() }
  }
}

@Suppress("unused")
fun visualPaddingsPanelOnlyComboBox(): JPanel {
  return panel {
    row("Combobox:") { JComboBox<String>(arrayOf("one", "two"))(growX) }
    row("Combobox Editable:") {
      val field = JComboBox<String>(arrayOf("one", "two"))
      field.isEditable = true
      field(growX)
    }
  }
}

@Suppress("unused")
fun visualPaddingsPanelOnlyButton(): JPanel {
  return panel {
    row("Button:") { button("label") {}.constraints(growX) }
  }
}

@Suppress("unused")
fun visualPaddingsPanelOnlyLabeledScrollPane(): JPanel {
  return panel {
    row("Description:") {
      scrollPane(JTextArea())
    }
  }
}

@Suppress("unused")
fun visualPaddingsPanelOnlyTextField(): JPanel {
  return panel {
    row("Text field:") { JTextField("text")() }
  }
}

fun createLafTestPanel(): JPanel {
  val spacing = createIntelliJSpacingConfiguration()
  val panel = JPanel(GridLayout(0, 1, spacing.horizontalGap, spacing.verticalGap))
  panel.add(JTextField("text"))
  panel.add(JPasswordField("secret"))
  panel.add(ComboBox<String>(arrayOf("one", "two")))

  val field = ComboBox<String>(arrayOf("one", "two"))
  field.isEditable = true
  panel.add(field)

  panel.add(JButton("label"))
  panel.add(CheckBox("enabled"))
  panel.add(JRadioButton("label"))
  panel.add(JBIntSpinner(0, 0, 7))
  panel.add(textFieldWithHistoryWithBrowseButton(null, "File", FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()))

  return panel
}

// titledRows is not enough to test because component align depends on comment components, so, pure titledRow must be tested
fun titledRow(): JPanel {
  return panel {
    titledRow("Remote settings") {
      row("Default notebook name:") { JTextField("")() }
      row("Spark version:") { JTextField("")() }
    }
  }
}

fun separatorAndComment() : JPanel {
  return panel {
    row("Label", separated = true) {
      textField({ "abc" }, {}).comment("comment")
    }
  }
}
