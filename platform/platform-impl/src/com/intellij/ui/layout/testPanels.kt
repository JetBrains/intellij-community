// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.UIBundle
import com.intellij.ui.components.*
import java.awt.Dimension
import java.awt.GridLayout
import javax.swing.*

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
    row("Button:") { button("label", growX) {} }
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
    row("Button:") { button("label", growX) {} }
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
      scrollPane(JTextArea(), pushX)

      cell(isVerticalFlow = true) {
        button("Accept Yours", growX) {}
        button("Accept Theirs", growX) {}
        button("Merge ...", growX) {}
      }
    }
  }
}

fun titledRows(): JPanel {
  return panel {
    titledRow("Async Profiler") {
      row { browserLink("Async profiler README.md", "https://github.com/jvm-profiling-tools/async-profiler") }
      row("Agent path:") { textFieldWithBrowseButton("", comment = "If field is empty bundled agent will be used") }
      row("Agent options:") { textFieldWithBrowseButton("", comment = "Don't add output format (collapsed is used) or output file options") }
    }
    titledRow("Java Flight Recorder") {
      row("JRE home:") {
        textFieldWithBrowseButton("", comment = "At least OracleJRE 9 or OpenJRE 11 is required to import dump")
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

// titledRows is not enough to test because component align depends on comment components, so, pure titledRow must be tested
fun titledRow(): JPanel {
  return panel {
    titledRow("Remote settings") {
      row("Default notebook name:") { JTextField("")() }
      row("Spark version:") { JTextField("")() }
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
