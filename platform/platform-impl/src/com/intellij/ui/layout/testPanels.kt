// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")
package com.intellij.ui.layout

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.UIBundle
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

fun fieldWithGear(): JPanel {
  return panel {
    row("Database:") {
      JTextField()()
      gearButton()
    }
    row("Master Password:") {
      JBPasswordField()()
    }
  }
}

fun fieldWithGearWithIndent(): JPanel {
  return panel {
    row {
      row("Database:") {
        JTextField()()
        gearButton()
      }
      row("Master Password:") {
        JBPasswordField()()
      }
    }
  }
}

fun alignFieldsInTheNestedGrid(): JPanel {
  return panel {
    buttonGroup {
      row {
        RadioButton("In KeePass")()
        row("Database:") {
          JTextField()()
          gearButton()
        }
        row("Master Password:") {
          JBPasswordField()(comment = "Stored using weak encryption.")
        }
      }
    }
  }
}

fun noteRowInTheDialog(): JPanel {
  val passwordField = JPasswordField()
  return panel {
    noteRow("Profiler requires access to the kernel-level API.\nEnter the sudo password to allow this. ")
    row("Sudo password:") { passwordField() }
    row { CheckBox(UIBundle.message("auth.remember.cb"), true)() }
    noteRow("Should be an empty row above as a gap. <a href=''>Click me</a>.") {
      System.out.println("Hello")
    }
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

fun titledRows(): JPanel {
  return panel {
    titledRow("Async Profiler") {
      row { browserLink("Async profiler README.md", "https://github.com/jvm-profiling-tools/async-profiler") }
      row("Agent path:") { textFieldWithBrowseButton("").comment("If field is empty bundled agent will be used") }
      row("Agent options:") { textFieldWithBrowseButton("").comment("Don't add output format (collapsed is used) or output file options") }
    }
    titledRow("Java Flight Recorder") {
      row("JRE home:") {
        textFieldWithBrowseButton("").comment("At least OracleJRE 9 or OpenJRE 11 is required to import dump")
      }
    }
  }
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
