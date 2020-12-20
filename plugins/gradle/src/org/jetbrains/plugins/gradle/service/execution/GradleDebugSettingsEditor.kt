// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.layout.*
import org.jetbrains.plugins.gradle.util.GradleBundle
import javax.swing.JCheckBox
import javax.swing.JComponent

class GradleDebugSettingsEditor : SettingsEditor<GradleRunConfiguration?>() {
  private val myScriptDebugCheckBox: JCheckBox = JCheckBox(GradleBundle.message("gradle.tasks.script.debugging"))
  private val myDebugAllCheckBox: JCheckBox  = JCheckBox(GradleBundle.message("gradle.tasks.debugging.all"))

  override fun resetEditorFrom(s: GradleRunConfiguration) {
    myScriptDebugCheckBox.isSelected = s.isScriptDebugEnabled
    myDebugAllCheckBox.isSelected = s.isDebugAllEnabled
  }

  override fun applyEditorTo(s: GradleRunConfiguration) {
    s.isScriptDebugEnabled = myScriptDebugCheckBox.isSelected
    s.isDebugAllEnabled = myDebugAllCheckBox.isSelected
  }

  override fun createEditor(): JComponent =
    panel {
      row {
        component(myScriptDebugCheckBox)
      }
      row {
        component(myDebugAllCheckBox)
      }
    }
}