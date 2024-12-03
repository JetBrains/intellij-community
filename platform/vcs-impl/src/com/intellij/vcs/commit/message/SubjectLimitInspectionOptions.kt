// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message

import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.project.Project
import com.intellij.ui.JBIntSpinner
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Internal
class SubjectLimitInspectionOptions(private val myInspection: SubjectLimitInspection) : ConfigurableUi<Project?> {
  private var myMarginSpinner: JBIntSpinner? = null
  private val myMainPanel: JPanel? = null

  private fun createUIComponents() {
    myMarginSpinner = JBIntSpinner(0, 0, 10000)
  }

  override fun reset(project: Project) {
    myMarginSpinner!!.setNumber(myInspection.RIGHT_MARGIN)
  }

  override fun isModified(project: Project): Boolean {
    return myMarginSpinner!!.getNumber() != myInspection.RIGHT_MARGIN
  }

  override fun apply(project: Project) {
    myInspection.RIGHT_MARGIN = myMarginSpinner!!.getNumber()
  }

  override fun getComponent(): JComponent {
    return myMainPanel!!
  }
}
