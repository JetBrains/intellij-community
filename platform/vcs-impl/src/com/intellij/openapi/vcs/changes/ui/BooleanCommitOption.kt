// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.checkin.CheckinHandlerUtil.disableWhenDumb
import com.intellij.openapi.vcs.configurable.CommitOptionsConfigurable
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.ui.components.JBCheckBox
import com.intellij.vcs.commit.isNonModalCommit
import org.jetbrains.annotations.Nls
import java.util.function.Consumer
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty0

open class BooleanCommitOption(
  private val checkinPanel: CheckinProjectPanel,
  @Nls text: String,
  disableWhenDumb: Boolean,
  private val getter: () -> Boolean,
  private val setter: Consumer<Boolean>
) : RefreshableOnComponent,
    UnnamedConfigurable {

  constructor(panel: CheckinProjectPanel, @Nls text: String, disableWhenDumb: Boolean, property: KMutableProperty0<Boolean>) :
    this(panel, text, disableWhenDumb, { property.get() }, Consumer { property.set(it) })

  protected val checkBox = JBCheckBox(text).apply {
    isFocusable = isInSettings || isInNonModalOptionsPopup || UISettings.shadowInstance.disableMnemonicsInControls
    if (disableWhenDumb && !isInSettings) disableWhenDumb(checkinPanel.project, this,
                                                          VcsBundle.message("changes.impossible.until.indices.are.up.to.date"))
  }

  private val isInSettings get() = checkinPanel is CommitOptionsConfigurable.CheckinPanel
  private val isInNonModalOptionsPopup get() = checkinPanel.isNonModalCommit

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