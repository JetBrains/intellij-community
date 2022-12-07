// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Placeholder
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.removeMnemonic
import org.jetbrains.annotations.Nls
import javax.swing.JCheckBox
import javax.swing.JComponent

class CommitOptionsPanel(private val project: Project,
                         private val actionNameSupplier: () -> @Nls String,
                         private val nonFocusable: Boolean) : CommitOptionsUi {
  val component: JComponent
  private lateinit var placeholder: Placeholder

  var isEmpty: Boolean = true
    private set

  private var visibleVcses: Set<AbstractVcs>? = null
  private val visibleVcsListeners = mutableListOf<Runnable>()

  init {
    val panel = panel {
      row {
        placeholder = placeholder()
          .align(Align.FILL)
      }.resizableRow()
    }
    component = ScrollPaneFactory.createScrollPane(panel, true)
  }

  override fun setOptions(options: CommitOptions) {
    val actionName = removeMnemonic(actionNameSupplier())

    visibleVcsListeners.clear()
    isEmpty = options.isEmpty

    placeholder.component = panel {
      for ((vcs, option) in options.vcsOptions) {
        group(vcs.displayName) {
          appendOptionRow(option)
        }.visibleIf(VcsVisiblePredicate(vcs))
      }

      val beforeOptions = options.beforeOptions
      if (beforeOptions.isNotEmpty()) {
        group(commitChecksGroupTitle(project, actionName)) {
          for (option in beforeOptions) {
            appendOptionRow(option)
          }
        }
      }

      val afterOptions = options.afterOptions
      if (afterOptions.isNotEmpty()) {
        group(message("border.standard.after.checkin.options.group", actionName)) {
          for (option in afterOptions) {
            appendOptionRow(option)
          }
        }
      }
    }

    // Hack: do not iterate over checkboxes in CommitDialog.
    if (nonFocusable) {
      UIUtil.forEachComponentInHierarchy(component) {
        if (it is JCheckBox) it.isFocusable = false
      }
    }
  }

  private fun Panel.appendOptionRow(option: RefreshableOnComponent) {
    row {
      cell(option.component)
        .align(Align.FILL)
    }
  }

  override fun setVisible(vcses: Collection<AbstractVcs>?) {
    visibleVcses = vcses?.toSet()
    for (listener in visibleVcsListeners) {
      listener.run()
    }
  }

  private inner class VcsVisiblePredicate(val vcs: AbstractVcs) : ComponentPredicate() {
    override fun addListener(listener: (Boolean) -> Unit) {
      visibleVcsListeners.add(Runnable { listener(invoke()) })
    }

    override fun invoke(): Boolean {
      return visibleVcses?.contains(vcs) ?: true
    }
  }

  companion object {
    fun commitChecksGroupTitle(project: Project, actionName: @Nls String): @Nls String {
      if (Registry.`is`("vcs.non.modal.post.commit.checks")) {
        if (ProjectLevelVcsManager.getInstance(project).allActiveVcss
            .any { vcs -> vcs.checkinEnvironment?.postCommitChangeConverter != null }) {
          return message("border.standard.checkin.options.group.with.post.commit", actionName)
        }
      }

      return message("border.standard.checkin.options.group", actionName)
    }
  }
}
