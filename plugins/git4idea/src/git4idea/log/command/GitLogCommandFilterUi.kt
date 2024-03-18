// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log.command

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.vcs.log.VcsLogDataPack
import com.intellij.vcs.log.VcsLogFilter
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx
import com.intellij.vcs.log.ui.filter.VcsLogTextFilterField
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import javax.swing.JComponent

class GitLogCommandFilterUi(filters: VcsLogFilterCollection?, filterConsumer: (VcsLogFilterCollection) -> Unit) : VcsLogFilterUiEx {
  private val eventDispatcher = EventDispatcher.create(VcsLogFilterUiEx.VcsLogFilterListener::class.java)

  private val textField = JBTextField().apply {
    addActionListener {
      filterConsumer(getFilters())
      eventDispatcher.multicaster.onFiltersChanged()
    }
  }
  private val commandComponent = simplePanel(2, 0)
    .addToLeft(JBLabel(GIT_LOG_LABEL))
    .addToCenter(textField).apply {
      border = JBUI.Borders.emptyLeft(5)
    }

  init {
    filters?.let { setFilters(filters) }
  }

  override fun getFilters(): VcsLogFilterCollection {
    if (textField.text.isNullOrBlank()) return VcsLogFilterObject.collection()
    return VcsLogFilterObject.collection(GitLogCommandFilter(textField.text))
  }

  override fun setFilters(collection: VcsLogFilterCollection) {
    textField.text = collection[GitLogCommandFilter.KEY]?.command ?: ""
  }

  override fun createActionGroup(): ActionGroup = DefaultActionGroup()

  override fun getTextFilterComponent(): VcsLogTextFilterField = object : VcsLogTextFilterField {
    override val component: JComponent get() = commandComponent
    override val focusedComponent: JComponent get() = textField
    override var text: String by textField::text
  }

  override fun updateDataPack(newDataPack: VcsLogDataPack) = Unit

  override fun addFilterListener(listener: VcsLogFilterUiEx.VcsLogFilterListener) = eventDispatcher.addListener(listener)
}

class GitLogCommandFilter(val command: @NlsSafe String) : VcsLogFilter {
  override fun getKey(): VcsLogFilterCollection.FilterKey<*> = KEY
  override fun getDisplayText(): String = command
  override fun toString(): String = "$GIT_LOG_LABEL $command"

  companion object {
    val KEY: VcsLogFilterCollection.FilterKey<GitLogCommandFilter> = VcsLogFilterCollection.FilterKey.create("command")
  }
}

private const val GIT_LOG_LABEL: @NlsSafe String = "git log"