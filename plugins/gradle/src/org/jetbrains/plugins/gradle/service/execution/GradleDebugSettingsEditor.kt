// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.plugins.gradle.util.GradleBundle
import javax.swing.JCheckBox
import javax.swing.JComponent

class GradleDebugSettingsEditor : SettingsEditor<GradleRunConfiguration?>() {

  private lateinit var myScriptDebugCheckBox: JCheckBox
  private lateinit var myReattachDebugProcess: JCheckBox
  private lateinit var myDebugAllCheckBox: JCheckBox

  override fun resetEditorFrom(s: GradleRunConfiguration) {
    myScriptDebugCheckBox.isSelected = s.isScriptDebugEnabled
    myReattachDebugProcess.isSelected = s.isReattachDebugProcess
    myDebugAllCheckBox.isSelected = s.isDebugAllEnabled
  }

  override fun applyEditorTo(s: GradleRunConfiguration) {
    s.isScriptDebugEnabled = myScriptDebugCheckBox.isSelected
    s.isReattachDebugProcess = myReattachDebugProcess.isSelected
    s.isDebugAllEnabled = myDebugAllCheckBox.isSelected
  }

  override fun createEditor(): JComponent =
    panel {
      row {
        myScriptDebugCheckBox = checkBox(GradleBundle.message("gradle.tasks.script.debugging")).component
      }
      row {
        myReattachDebugProcess = checkBox(GradleBundle.message("gradle.tasks.reattach.debug.process"))
          .comment(GradleBundle.message("gradle.tasks.reattach.debug.process.comment"))
          .component
      }
      row {
        myDebugAllCheckBox = checkBox(GradleBundle.message("gradle.tasks.debugging.all"))
          .comment(GradleBundle.message("gradle.tasks.debugging.all.comment"))
          .component
      }
    }
}