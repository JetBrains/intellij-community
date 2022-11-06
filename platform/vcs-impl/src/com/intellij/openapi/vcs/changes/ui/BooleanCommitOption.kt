// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.checkin.CheckinHandlerUtil
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.ui.components.JBCheckBox
import org.jetbrains.annotations.Nls
import java.util.function.Consumer
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty0

open class BooleanCommitOption(
  private val project: Project,
  @Nls text: String,
  private val disableWhenDumb: Boolean,
  private val getter: () -> Boolean,
  private val setter: Consumer<Boolean>
) : RefreshableOnComponent,
    UnnamedConfigurable {

  constructor(project: Project, @Nls text: String, disableWhenDumb: Boolean, property: KMutableProperty0<Boolean>) :
    this(project, text, disableWhenDumb, { property.get() }, Consumer { property.set(it) })

  constructor(panel: CheckinProjectPanel, @Nls text: String, disableWhenDumb: Boolean, getter: () -> Boolean, setter: Consumer<Boolean>) :
    this(panel.project, text, disableWhenDumb, getter, setter)

  constructor(panel: CheckinProjectPanel, @Nls text: String, disableWhenDumb: Boolean, property: KMutableProperty0<Boolean>) :
    this(panel.project, text, disableWhenDumb, { property.get() }, Consumer { property.set(it) })

  protected val checkBox = JBCheckBox(text)

  private var isInSettings = false

  override fun saveState() {
    setter.accept(checkBox.isSelected)
  }

  override fun restoreState() {
    checkBox.isSelected = getter()
    if (disableWhenDumb && !isInSettings) {
      CheckinHandlerUtil.disableWhenDumb(project, checkBox,
                                         VcsBundle.message("changes.impossible.until.indices.are.up.to.date"))
    }
  }

  override fun getComponent(): JComponent = checkBox

  override fun createComponent(): JComponent {
    isInSettings = true
    return component
  }

  override fun isModified() = checkBox.isSelected != getter()

  override fun apply() = saveState()

  override fun reset() = restoreState()
}