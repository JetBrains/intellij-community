// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message

import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Internal
class BodyLimitInspectionOptions(private val myInspection: BodyLimitInspection) : ConfigurableUi<Project?> {
  private var myMarginSpinner: JBIntSpinner? = null
  private val myMainPanel: JPanel? = null
  private val myShowRightMargin: JBCheckBox? = null
  private val myWrapWhenTyping: JBCheckBox? = null

  private fun createUIComponents() {
    myMarginSpinner = JBIntSpinner(0, 0, 10000)
  }

  override fun reset(project: Project) {
    val settings = VcsConfiguration.getInstance(project)

    myMarginSpinner!!.setNumber(myInspection.RIGHT_MARGIN)
    myShowRightMargin!!.setSelected(settings.USE_COMMIT_MESSAGE_MARGIN)
    myWrapWhenTyping!!.setSelected(settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN)
  }

  override fun isModified(project: Project): Boolean {
    val settings = VcsConfiguration.getInstance(project)

    return myMarginSpinner!!.getNumber() != myInspection.RIGHT_MARGIN || myShowRightMargin!!.isSelected() != settings.USE_COMMIT_MESSAGE_MARGIN || myWrapWhenTyping!!.isSelected() != settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN
  }

  override fun apply(project: Project) {
    val settings = VcsConfiguration.getInstance(project)

    myInspection.RIGHT_MARGIN = myMarginSpinner!!.getNumber()
    settings.USE_COMMIT_MESSAGE_MARGIN = myShowRightMargin!!.isSelected()
    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = myWrapWhenTyping!!.isSelected()
  }

  override fun getComponent(): JComponent {
    return myMainPanel!!
  }
}
