// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.dvcs.ui.DvcsBundle.message
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.layout.*
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.VcsExecutablePathSelector
import git4idea.GitVcs
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepositoryManager
import javax.swing.JLabel


internal class GitVcsPanel(private val project: Project,
                           private val applicationSettings: GitVcsApplicationSettings,
                           private val projectSettings: GitVcsSettings,
                           private val sharedSettings: GitSharedSettings,
                           private val executableManager: GitExecutableManager) :
  BoundConfigurable(GitVcs.NAME, "project.propVCSSupport.VCSs.Git"),
  SearchableConfigurable {

  @Volatile
  private var versionCheckRequested = false

  private val pathSelector: VcsExecutablePathSelector = createPathSelector()

  private lateinit var branchUpdateInfoRow: Row
  private lateinit var branchUpdateInfoCommentRow: Row
  private lateinit var supportedBranchUpLabel: JLabel

  private fun createPathSelector() = VcsExecutablePathSelector("Git") { path ->
    val pathToGit = path ?: executableManager.detectedExecutable
    object : Task.Modal(project, GitBundle.getString("git.executable.version.progress.title"), true) {
      private lateinit var gitVersion: GitVersion

      override fun run(indicator: ProgressIndicator) {
        executableManager.dropVersionCache(pathToGit)
        gitVersion = executableManager.identifyVersion(pathToGit)
      }

      override fun onThrowable(error: Throwable) {
        GitExecutableProblemsNotifier.showExecutionErrorDialog(error, project)
      }

      override fun onSuccess() {
        if (gitVersion.isSupported) {
          Messages.showInfoMessage(pathSelector.mainPanel,
                                   GitBundle.message("git.executable.version.is", gitVersion.presentation),
                                   GitBundle.getString("git.executable.version.success.title"))
        }
        else {
          GitExecutableProblemsNotifier.showUnsupportedVersionDialog(gitVersion, project)
        }
      }
    }.queue()
  }

  private fun getCurrentExecutablePath(): String? = pathSelector.currentPath?.takeIf { it.isNotBlank() }

  private fun LayoutBuilder.gitExecutableRow() = row {
    pathSelector.mainPanel(growX)
      .onReset {
        val projectSettingsPathToGit = projectSettings.pathToGit
        pathSelector.reset(applicationSettings.savedPathToGit,
                           projectSettingsPathToGit != null,
                           projectSettingsPathToGit,
                           executableManager.detectedExecutable)
        updateBranchUpdateInfoRow()
      }
      .onIsModified {
        val projectSettingsPathToGit = projectSettings.pathToGit
        val currentPath = getCurrentExecutablePath()
        if (pathSelector.isOverridden) {
          currentPath != projectSettingsPathToGit
        }
        else {
          currentPath != applicationSettings.savedPathToGit || projectSettingsPathToGit != null
        }
      }
      .onApply {
        val executablePathOverridden = pathSelector.isOverridden
        val currentPath = getCurrentExecutablePath()
        if (executablePathOverridden) {
          projectSettings.pathToGit = currentPath
        }
        else {
          applicationSettings.setPathToGit(currentPath)
          projectSettings.pathToGit = null
        }
        validateExecutableOnceAfterClose()
        updateBranchUpdateInfoRow()
      }
  }

  /**
   * Special method to check executable after it has been changed through settings
   */
  private fun validateExecutableOnceAfterClose() {
    if (!versionCheckRequested) {
      ApplicationManager.getApplication().invokeLater(
        {
          object : Task.Backgroundable(project, GitBundle.getString("git.executable.version.progress.title"), true) {
            override fun run(indicator: ProgressIndicator) {
              executableManager.testGitExecutableVersionValid(project)
            }
          }.queue()
          versionCheckRequested = false
        },
        ModalityState.NON_MODAL)
      versionCheckRequested = true
    }
  }

  private fun updateBranchUpdateInfoRow() {
    val branchInfoSupported = GitVersionSpecialty.INCOMING_OUTGOING_BRANCH_INFO.existsIn(project)
    branchUpdateInfoRow.enabled = Registry.`is`("git.update.incoming.outgoing.info") && branchInfoSupported
    branchUpdateInfoCommentRow.visible = !branchInfoSupported
    supportedBranchUpLabel.foreground = if (!branchInfoSupported && projectSettings.incomingCheckStrategy != GitIncomingCheckStrategy.Never) {
      DialogWrapper.ERROR_FOREGROUND_COLOR
    }
    else {
      UIUtil.getContextHelpForeground()
    }
  }

  private fun LayoutBuilder.branchUpdateInfoRow() {
    branchUpdateInfoRow = row {
      supportedBranchUpLabel = JBLabel("Supported for Git 2.9+")
      cell {
        label("Explicitly check for incoming commits on remotes: ")
        comboBox(
          EnumComboBoxModel(GitIncomingCheckStrategy::class.java),
          {
            projectSettings.incomingCheckStrategy
          },
          { selectedStrategy ->
            projectSettings.incomingCheckStrategy = selectedStrategy as GitIncomingCheckStrategy
            updateBranchUpdateInfoRow()
            GitBranchIncomingOutgoingManager.getInstance(project).updateIncomingScheduling()
          })
      }
      branchUpdateInfoCommentRow = row {
        supportedBranchUpLabel()
      }
    }
  }

  override fun getId() = "vcs.${GitVcs.NAME}"

  override fun createPanel(): DialogPanel = panel {
    gitExecutableRow()
    if (project.isDefault || GitRepositoryManager.getInstance(project).moreThanOneRoot()) {
      row {
        checkBox(message("sync.setting"),
                 { projectSettings.syncSetting == DvcsSyncSettings.Value.SYNC },
                 { projectSettings.syncSetting = if (it) DvcsSyncSettings.Value.SYNC else DvcsSyncSettings.Value.DONT_SYNC })
          .component.toolTipText = message("sync.setting.description", "Git")
      }
    }
    row {
      checkBox("Commit automatically on cherry-pick",
               { applicationSettings.isAutoCommitOnCherryPick },
               { applicationSettings.isAutoCommitOnCherryPick = it })
    }
    row {
      checkBox("Add the 'cherry-picked from <hash>' suffix when picking commits pushed to protected branches",
               { projectSettings.shouldAddSuffixToCherryPicksOfPublishedCommits() },
               { projectSettings.setAddSuffixToCherryPicks(it) })
    }
    row {
      checkBox("Warn if CRLF line separators are about to be committed",
               { projectSettings.warnAboutCrlf() },
               { projectSettings.setWarnAboutCrlf(it) })
    }
    row {
      checkBox("Warn when committing in detached HEAD or during rebase",
               { projectSettings.warnAboutDetachedHead() },
               { projectSettings.setWarnAboutDetachedHead(it) })
    }
    branchUpdateInfoRow()
    row {
      cell {
        label("Update method:")
        comboBox(
          EnumComboBoxModel(UpdateMethod::class.java),
          { projectSettings.updateMethod },
          { projectSettings.updateMethod = it!! },
          renderer = SimpleListCellRenderer.create<UpdateMethod>("") { value ->
            value.name.replace('_', ' ').toLowerCase().capitalize()
          }
        )
      }
    }
    row {
      checkBox("Auto-update if push of the current branch was rejected",
               { projectSettings.autoUpdateIfPushRejected() },
               { projectSettings.setAutoUpdateIfPushRejected(it) })
    }
    row {
      val previewPushOnCommitAndPush = checkBox("Show Push dialog for Commit and Push",
                                                { projectSettings.shouldPreviewPushOnCommitAndPush() },
                                                { projectSettings.setPreviewPushOnCommitAndPush(it) })
      row {
        checkBox("Show Push dialog only when committing to protected branches",
                 { projectSettings.isPreviewPushProtectedOnly },
                 { projectSettings.isPreviewPushProtectedOnly = it })
          .enableIf(previewPushOnCommitAndPush.selected)
      }
    }
    row {
      cell {
        label("Protected branches:")
        val protectedBranchesField = ExpandableTextField(ParametersListUtil.COLON_LINE_PARSER, ParametersListUtil.COLON_LINE_JOINER)
        protectedBranchesField(growX)
          .withBinding<List<String>>(
            { ParametersListUtil.COLON_LINE_PARSER.`fun`(it.text) },
            { component, value -> component.text = ParametersListUtil.COLON_LINE_JOINER.`fun`(value) },
            PropertyBinding(
              { sharedSettings.forcePushProhibitedPatterns },
              { sharedSettings.forcePushProhibitedPatterns = it })
          )
      }
    }
    row {
      checkBox("Use credential helper",
               { applicationSettings.isUseCredentialHelper },
               { applicationSettings.isUseCredentialHelper = it })
    }
  }
}