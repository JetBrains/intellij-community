// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.checkin.CheckinHandlerUtil
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.ui.NonFocusableCheckBox
import java.util.function.Consumer
import javax.swing.JComponent

open class BooleanCommitOption(panel: CheckinProjectPanel,
                               text: String,
                               disableWhenDumb: Boolean,
                               private val getter: () -> Boolean,
                               private val setter: Consumer<Boolean>) : RefreshableOnComponent, UnnamedConfigurable {
  protected val checkBox = NonFocusableCheckBox(text).also {
    if (disableWhenDumb) CheckinHandlerUtil.disableWhenDumb(panel.project, it, "Impossible until indices are up-to-date")
  }

  override fun refresh() {
  }

  override fun saveState() {
    setter.accept(checkBox.isSelected)
  }

  override fun restoreState() {
    checkBox.isSelected = getter()
  }

  override fun getComponent(): JComponent = checkBox

  override fun createComponent() = component

  override fun isModified() = checkBox.isSelected != getter()

  override fun apply() = saveState()

  override fun reset() = restoreState()
}