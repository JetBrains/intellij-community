// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("HardCodedStringLiteral")
package com.intellij.ui.layout

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.UIBundle
import com.intellij.ui.components.*
import java.awt.Dimension
import java.awt.GridLayout
import java.util.function.Supplier
import javax.swing.*

@Deprecated(message = "New UI DSL is implemented, see com.intellij.ui.dsl. Whole file should be removed")
fun labelRowShouldNotGrow(): JPanel {
  return panel {
    row("Create Android module") { CheckBox("FooBar module name foo")() }
    row("Android module name:") { JTextField("input")() }
  }
}

fun secondColumnSmallerPanel(): JPanel {
  val selectForkButton = JButton("Select Other Fork")

  val branchCombobox = ComboBox<String>()
  val diffButton = JButton("Show Diff")

  val titleTextField = JTextField()

  val panel = panel {
    row("Base fork:") {
      JComboBox<String>(arrayOf())(growX, CCFlags.pushX)
      selectForkButton(growX)
    }
    row("Base branch:") {
      branchCombobox(growX, pushX)
      diffButton(growX)
    }
    row("Title:") { titleTextField() }
    row("Description:") {
      scrollPane(JTextArea())
    }
  }

  // test scrollPane
  panel.preferredSize = Dimension(512, 256)
  return panel
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

fun visualPaddingsPanel(): JPanel {
  // we use growX to test right border
  return panel {
    row("Text field:") { JTextField("text")() }
    row("Password:") { JPasswordField("secret")() }
    row("Combobox:") { JComboBox<String>(arrayOf("one", "two"))(growX) }
    row("Combobox Editable:") {
      val field = JComboBox<String>(arrayOf("one", "two"))
      field.isEditable = true
      field(growX)
    }
    row("Button:") { button("label") {}.constraints(growX) }
    row("CheckBox:") { CheckBox("enabled")() }
    row("RadioButton:") { JRadioButton("label")() }
    row("Spinner:") { JBIntSpinner(0, 0, 7)() }
    row("Text with browse:") { textFieldWithBrowseButton("File") }
    // test text baseline alignment
    row("All:") {
      cell {
        JTextField("t")()
        JPasswordField("secret")()
        JComboBox<String>(arrayOf("c1", "c2"))(growX)
        button("b") {}
        CheckBox("c")()
        JRadioButton("rb")()
      }
    }
    row("Scroll pane:") {
      scrollPane(JTextArea("first line baseline equals to label"))
    }
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

fun jbTextField(): JPanel {
  val passwordField = JBPasswordField()
  return panel {
    noteRow("Enter credentials for bitbucket.org")
    row("Username:") { JTextField("develar")() }
    row("Password:") { passwordField() }
    row {
      JBCheckBox(UIBundle.message("auth.remember.cb"), true)()
    }
  }
}

fun cellPanel(): JPanel {
  return panel {
    row("Repository:") {
      cell {
        ComboBox<String>()(comment = "Use File -> Settings Repository... to configure")
        JButton("Delete")()
      }
    }
    row {
      // need some pushx/grow component to test label cell grow policy if there is cell with several components
      scrollPane(JTextArea())
    }
  }
}

fun commentAndPanel(): JPanel {
  return panel {
    row("Repository:") {
      cell {
        checkBox("Auto Sync", comment = "Use File -> Settings Repository... to configure")
      }
    }
    row {
      panel("Foo", JScrollPane(JTextArea()))
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

fun withVerticalButtons(): JPanel {
  return panel {
    row {
      label("<html>Merging branch <b>foo</b> into <b>bar</b>")
    }
    row {
      scrollPane(JTextArea()).constraints(pushX)

      cell(isVerticalFlow = true) {
        button("Accept Yours") {}.constraints(growX)
        button("Accept Theirs") {}.constraints(growX)
        button("Merge ...") {}.constraints(growX)
      }
    }
  }
}

fun withSingleVerticalButton(): JPanel {
  return panel {
    row {
      label("<html>Merging branch <b>foo</b> into <b>bar</b>")
    }
    row {
      scrollPane(JTextArea()).constraints(pushX)

      cell(isVerticalFlow = true) {
        button("Merge ...") {}.constraints(growX)
      }
    }
  }
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

fun spannedCheckbox(): JPanel {
  return panel {
    buttonGroup {
      row {
        RadioButton("In KeePass")()
        row("Database:") {
          // comment can lead to broken layout, so, test it
          JTextField("test")(comment = "Stored using weak encryption. It is recommended to store on encrypted volume for additional security.")
        }

        row {
          cell {
            checkBox("Protect master password using PGP key")
            val comboBox = ComboBox(arrayOf("Foo", "Bar"))
            comboBox.isVisible = false
            comboBox(growPolicy = GrowPolicy.MEDIUM_TEXT)
          }
        }
      }

      row {
        RadioButton("Do not save, forget passwords after restart")()
      }
    }
  }
}

fun checkboxRowsWithBigComponents(): JPanel {
  return panel {
    row {
      CheckBox("Sample checkbox label")()
    }
    row {
      CheckBox("Sample checkbox label")()
    }
    row {
      CheckBox("Sample checkbox label")()
      ComboBox(DefaultComboBoxModel(arrayOf("asd", "asd")))()
    }
    row {
      CheckBox("Sample checkbox label")()
    }
    row {
      CheckBox("Sample checkbox label")()
      ComboBox(DefaultComboBoxModel(arrayOf("asd", "asd")))()
    }
    row {
      CheckBox("Sample checkbox label")()
      ComboBox(DefaultComboBoxModel(arrayOf("asd", "asd")))()
    }
    row {
      CheckBox("Sample checkbox label")()
      JBTextField()()
    }
    row {
      cell(isFullWidth = true) {
        CheckBox("Sample checkbox label")()
      }
    }
    row {
      cell(isFullWidth = true) {
        CheckBox("Sample checkbox label")()
        JBTextField()()
      }
    }
    row {
      cell(isFullWidth = true) {
        CheckBox("Sample checkbox label")()
        comment("commentary")
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

fun sampleConfigurablePanel(): JPanel {
  return panel {
    titledRow("Settings") {
      row { checkBox("Some test option") }
      row { checkBox("Another test option") }
    }
    titledRow("Options") {
      row { checkBox("Some test option") }
      row {
        buttonGroup("Radio group") {
          row { radioButton("Option 1") }
          row { radioButton("Option 2") }
        }
      }
      row {
        buttonGroup("Radio group") {
          row { radioButton("Option 1", comment = "Comment for the Option 1") }
          row { radioButton("Option 2") }
        }
      }
    }
    titledRow("Test") {
      row("Header") { JTextField()() }
      row("Longer Header") { checkBox("Some long description", comment = "Comment for the checkbox with longer header.") }
      row("Header") { JPasswordField()() }
      row("Header") { comboBox(DefaultComboBoxModel(arrayOf("Option 1", "Option 2")), { null }, {}) }
    }
  }
}

private data class TestOptions(var threadDumpDelay: Int, var enableLargeIndexing: Boolean, var largeIndexFilesCount: Int)

fun checkBoxFollowedBySpinner(): JPanel {
  val testOptions = TestOptions(50, true, 100)
  return panel {
    row(label = "Thread dump capture delay (ms):") {
      spinner(testOptions::threadDumpDelay, 50, 5000, 50)
    }
    row {
      val c = checkBox("Create", testOptions::enableLargeIndexing).actsAsLabel()
      spinner(testOptions::largeIndexFilesCount, 100, 1_000_000, 1_000)
        .enableIf(c.selected)
      label("files to start background indexing")
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

fun rowWithIndent(): JPanel {
  return panel {
    row("Zero") {
      subRowIndent = 0
      row("Bar 0") {
      }
    }
    row("One") {
      subRowIndent = 1

      row("Bar 1") {
      }
    }
    row("Two") {
      subRowIndent = 2

      row("Bar 2") {
      }
    }
  }
}

fun rowWithHiddenComponents(): JPanel {
  val label1 = JLabel("test1")
  val label2 = JLabel("test2")
  val label3 = JLabel("test3")
  val button1 = object : ToggleAction(Supplier{"button"}, null) {
    override fun isSelected(e: AnActionEvent): Boolean {
      return label1.isVisible
    }
    override fun setSelected(e: AnActionEvent, state: Boolean) {
      label1.isVisible = state
    }
  }
  val button2 = object : ToggleAction(Supplier{"button"}, null) {
    override fun isSelected(e: AnActionEvent): Boolean {
      return label2.isVisible
    }
    override fun setSelected(e: AnActionEvent, state: Boolean) {
      label2.isVisible = state
    }
  }
  val button3 = object : ToggleAction(Supplier{"button"}, null) {
    override fun isSelected(e: AnActionEvent): Boolean {
      return label3.isVisible
    }
    override fun setSelected(e: AnActionEvent, state: Boolean) {
      label3.isVisible = state
    }
  }
  return panel {
    row {
      component(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, DefaultActionGroup(button1, button2, button3), true).component)
    }
    row {
      component(label1)
    }
    row {
      component(label2)
    }
    row {
      component(label3)
    }
  }
}
