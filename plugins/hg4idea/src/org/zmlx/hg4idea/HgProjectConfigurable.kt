// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea

import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.VcsExecutablePathSelector
import com.intellij.util.ui.VcsExecutablePathSelector.ExecutableHandler
import org.jetbrains.annotations.Nls
import org.zmlx.hg4idea.util.HgUtil
import org.zmlx.hg4idea.util.HgVersion

class HgProjectConfigurable(val project: Project)
  : BoundSearchableConfigurable(getConfigurableDisplayName(),
                                "project.propVCSSupport.VCSs.Mercurial",
                                "vcs.Mercurial") {
  companion object {
    @JvmStatic
    fun getConfigurableDisplayName(): @Nls String {
      return HgBundle.message("hg4idea.mercurial")
    }
  }

  override fun createPanel(): DialogPanel {
    val disposable = disposable!!
    val globalSettings = HgGlobalSettings.getInstance()
    val projectSettings = HgProjectSettings.getInstance(project)

    return panel {
      row {
        val pathSelector = VcsExecutablePathSelector(HgVcs.DISPLAY_NAME.get(), disposable,
                                                     ExecutableHandler { executable -> testExecutable(executable) })
        cell(pathSelector.mainPanel)
          .horizontalAlign(HorizontalAlign.FILL)
          .onReset {
            pathSelector.setAutoDetectedPath(HgExecutableManager.getInstance().defaultExecutable)
            pathSelector.reset(globalSettings.hgExecutable,
                               projectSettings.isHgExecutableOverridden,
                               projectSettings.hgExecutable)
          }
          .onIsModified {
            pathSelector.isModified(globalSettings.hgExecutable,
                                    projectSettings.isHgExecutableOverridden,
                                    projectSettings.hgExecutable)
          }
          .onApply {
            if (pathSelector.isOverridden) {
              projectSettings.hgExecutable = pathSelector.currentPath
              projectSettings.isHgExecutableOverridden = pathSelector.isOverridden
            }
            else {
              globalSettings.hgExecutable = pathSelector.currentPath
              projectSettings.hgExecutable = null
              projectSettings.isHgExecutableOverridden = pathSelector.isOverridden
            }
            HgVcs.getInstance(project)!!.checkVersion()
          }
      }
      row {
        checkBox(HgBundle.message("hg4idea.configuration.check.incoming.outgoing"))
          .bindSelected(projectSettings::isCheckIncomingOutgoing, projectSettings::setCheckIncomingOutgoing)
      }
      row {
        checkBox(HgBundle.message("hg4idea.configuration.ignore.whitespace.in.annotate"))
          .bindSelected(projectSettings::isWhitespacesIgnoredInAnnotations, projectSettings::setIgnoreWhitespacesInAnnotations)
      }

      if (project.isDefault || HgUtil.getRepositoryManager(project).moreThanOneRoot()) {
        row {
          checkBox(DvcsBundle.message("sync.setting"))
            .bindSelected({ projectSettings.syncSetting == DvcsSyncSettings.Value.SYNC },
                          { isSelected -> projectSettings.syncSetting = if (isSelected) DvcsSyncSettings.Value.SYNC else DvcsSyncSettings.Value.DONT_SYNC })
            .gap(RightGap.SMALL)
          contextHelp(DvcsBundle.message("sync.setting.description", HgVcs.DISPLAY_NAME.get()))
        }
      }
    }
  }

  private fun testExecutable(executable: String) {
    object : Task.Modal(project, HgBundle.message("hg4idea.configuration.identifying.version"), true) {
      var version: HgVersion? = null

      override fun run(indicator: ProgressIndicator) {
        version = HgVersion.identifyVersion(myProject!!, executable)
      }

      override fun onSuccess() {
        Messages.showInfoMessage(project, HgBundle.message("hg4idea.configuration.version", version.toString()),
                                 HgBundle.message("hg4idea.run.success.title"))
      }

      override fun onThrowable(error: Throwable) {
        Messages.showErrorDialog(project, error.message, HgBundle.message("hg4idea.run.failed.title"))
      }
    }.queue()
  }
}