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
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.vcs.commit.CommitSessionCollector
import org.jetbrains.annotations.Nls
import java.util.function.Consumer
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty0

/**
 * Prefer using [BooleanCommitOption.create] and [BooleanCommitOption.createLink].
 */
open class BooleanCommitOption(
  protected val project: Project,
  @Nls text: String,
  private val disableWhenDumb: Boolean,
  protected val getter: () -> Boolean,
  protected val setter: Consumer<Boolean>
) : RefreshableOnComponent,
    UiDslUnnamedConfigurable.Simple() {

  constructor(project: Project, @Nls text: String, disableWhenDumb: Boolean, property: KMutableProperty0<Boolean>) :
    this(project, text, disableWhenDumb, { property.get() }, Consumer { property.set(it) })

  constructor(panel: CheckinProjectPanel, @Nls text: String, disableWhenDumb: Boolean, getter: () -> Boolean, setter: Consumer<Boolean>) :
    this(panel.project, text, disableWhenDumb, getter, setter)

  constructor(panel: CheckinProjectPanel, @Nls text: String, disableWhenDumb: Boolean, property: KMutableProperty0<Boolean>) :
    this(panel.project, text, disableWhenDumb, { property.get() }, Consumer { property.set(it) })

  protected val checkBox = JBCheckBox(text)

  protected var isInSettings = false
    private set

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
   * Implement [com.intellij.openapi.options.UiDslUnnamedConfigurable]
   */
  final override fun Panel.createContent() {
    isInSettings = true
    createOptionContent()
  }

  protected open fun Panel.createOptionContent() {
    row {
      cell(checkBox).also {
        if (isInSettings) it.bindSelected(getter, setter::accept)
      }
    }
  }

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

  companion object {
    @JvmStatic
    fun create(project: Project,
               checkinHandler: CheckinHandler?,
               disableWhenDumb: Boolean,
               @Nls text: String,
               getter: () -> Boolean,
               setter: Consumer<Boolean>): RefreshableOnComponent {
      val commitOption = BooleanCommitOption(project, text, disableWhenDumb, getter, setter)
      if (checkinHandler != null) commitOption.withCheckinHandler(checkinHandler)
      return commitOption
    }

    @JvmStatic
    fun create(project: Project,
               checkinHandler: CheckinHandler?,
               disableWhenDumb: Boolean,
               @Nls text: String,
               property: KMutableProperty0<Boolean>): RefreshableOnComponent {
      return create(project, checkinHandler, disableWhenDumb, text, { property.get() }, { property.set(it) })
    }

    @JvmStatic
    fun createLink(project: Project,
                   checkinHandler: CheckinHandler?,
                   disableWhenDumb: Boolean,
                   @Nls text: String,
                   property: KMutableProperty0<Boolean>,
                   linkText: @Nls String,
                   linkCallback: LinkListener<LinkContext>): RefreshableOnComponent {
      return createLink(project, checkinHandler, disableWhenDumb, text, { property.get() }, { property.set(it) }, linkText, linkCallback)
    }

    @JvmStatic
    fun createLink(project: Project,
                   checkinHandler: CheckinHandler?,
                   disableWhenDumb: Boolean,
                   @Nls text: String,
                   getter: () -> Boolean,
                   setter: Consumer<Boolean>,
                   linkText: @Nls String,
                   linkCallback: LinkListener<LinkContext>): RefreshableOnComponent {
      val commitOption = BooleanCommitOptionWithLink(project, text, disableWhenDumb, getter, setter, linkText, linkCallback)
      if (checkinHandler != null) commitOption.withCheckinHandler(checkinHandler)
      return commitOption
    }
  }

  interface LinkContext {
    fun setCheckboxText(text: @Nls String)
  }
}

private class BooleanCommitOptionWithLink(project: Project,
                                          text: @Nls String,
                                          disableWhenDumb: Boolean,
                                          getter: () -> Boolean,
                                          setter: Consumer<Boolean>,
                                          val linkText: @Nls String,
                                          val linkCallback: LinkListener<LinkContext>)
  : BooleanCommitOption(project, text, disableWhenDumb, getter, setter), BooleanCommitOption.LinkContext {
  override fun Panel.createOptionContent() {
    val configureFilterLink = LinkLabel(linkText, null, linkCallback, this@BooleanCommitOptionWithLink)
    row {
      cell(checkBox).also {
        if (isInSettings) it.bindSelected(getter, setter::accept)
      }
      cell(configureFilterLink)
    }
  }

  override fun setCheckboxText(text: @Nls String) {
    checkBox.text = text
  }
}

