// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.options.DslConfigurableBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsListener
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.data.index.VcsLogPersistentIndex
import com.intellij.vcs.log.impl.VcsLogSharedSettings
import com.intellij.vcsUtil.VcsUtil

class VcsLogSharedSettingsConfigurable(private val project: Project) : DslConfigurableBase() {
  private val settings get() = project.service<VcsLogSharedSettings>()

  override fun createPanel(): DialogPanel {
    val allVcsKeys = ProjectLevelVcsManager.getInstance(project).allActiveVcss.mapTo(mutableSetOf()) { it.keyInstanceMethod }
    val indexedVcsKeys = VcsLogPersistentIndex.getAvailableIndexers(project).mapTo(mutableSetOf()) { it.supportedVcs }
    val vcsNamesToShow = if (indexedVcsKeys != allVcsKeys) {
      indexedVcsKeys.mapNotNull { VcsUtil.findVcsByKey(project, it)?.displayName }.joinToString()
    }
    else ""
    return panel {
      group(VcsLogBundle.message("vcs.log.settings.group.title")) {
        row {
          checkBox(CheckboxDescriptor(VcsLogBundle.message("vcs.log.settings.enable.index.checkbox", vcsNamesToShow.length, vcsNamesToShow),
                                      settings::isIndexSwitchedOn, settings::setIndexSwitchedOn))
            .comment(VcsLogBundle.message("vcs.log.settings.enable.index.checkbox.comment"))
            .enabledIf(VcsLogIndexAvailabilityPredicate(project, disposable!!))
        }
      }
    }
  }
}

private class VcsLogIndexAvailabilityPredicate(private val project: Project, private val disposable: Disposable) : ComponentPredicate() {
  private val isVcsLogIndexAvailable get() = VcsLogPersistentIndex.getAvailableIndexers(project).isNotEmpty()

  override fun addListener(listener: (Boolean) -> Unit) {
    project.messageBus.connect(disposable).subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED,
                                                     VcsListener { listener(isVcsLogIndexAvailable) })
  }

  override fun invoke() = isVcsLogIndexAvailable
}