// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerUtil
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.vcs.commit.CommitSessionCollector
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
    UiDslUnnamedConfigurable {

  constructor(project: Project, @Nls text: String, disableWhenDumb: Boolean, property: KMutableProperty0<Boolean>) :
    this(project, text, disableWhenDumb, { property.get() }, Consumer { property.set(it) })

  constructor(panel: CheckinProjectPanel, @Nls text: String, disableWhenDumb: Boolean, getter: () -> Boolean, setter: Consumer<Boolean>) :
    this(panel.project, text, disableWhenDumb, getter, setter)

  constructor(panel: CheckinProjectPanel, @Nls text: String, disableWhenDumb: Boolean, property: KMutableProperty0<Boolean>) :
    this(panel.project, text, disableWhenDumb, { property.get() }, Consumer { property.set(it) })

  protected val checkBox = JBCheckBox(text)

  private var isInSettings = false

  private var checkinHandler: CheckinHandler? = null
  private var isDuringUpdate: Boolean = false

  init {
    checkBox.addActionListener {
      if (checkinHandler != null && !isDuringUpdate) {
        CommitSessionCollector.getInstance(project).logCommitCheckToggled(checkinHandler!!, isInSettings, checkBox.isSelected)
      }
    }
  }

  override fun saveState() {
    setter.accept(checkBox.isSelected)
  }

  override fun restoreState() {
    setSelected(getter())
    if (disableWhenDumb && !isInSettings) {
      CheckinHandlerUtil.disableWhenDumb(project, checkBox,
                                         VcsBundle.message("changes.impossible.until.indices.are.up.to.date"))
    }
  }

  /**
   * Implement [com.intellij.openapi.vcs.ui.RefreshableOnComponent]
   */
  final override fun getComponent(): JComponent = panel {
    createOptionContent()
  }

  /**
   * Implement [com.intellij.openapi.options.UnnamedConfigurable]
   */
  final override fun createComponent(): JComponent {
    isInSettings = true
    return panel {
      createOptionContent()
    }
  }

  /**
   * Implement [com.intellij.openapi.options.UiDslUnnamedConfigurable]
   */
  final override fun Panel.createContent() {
    isInSettings = true
    createOptionContent()
  }

  protected open fun Panel.createOptionContent() {
    row {
      cell(checkBox)
    }
  }

  override fun isModified() = checkBox.isSelected != getter()

  override fun apply() = saveState()

  override fun reset() = restoreState()

  protected fun setSelected(value: Boolean) {
    isDuringUpdate = true
    try {
      checkBox.isSelected = value
    }
    finally {
      isDuringUpdate = false
    }
  }

  fun withCheckinHandler(checkinHandler: CheckinHandler): BooleanCommitOption {
    this.checkinHandler = checkinHandler
    return this
  }
}