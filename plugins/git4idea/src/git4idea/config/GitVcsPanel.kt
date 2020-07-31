// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.dvcs.ui.DvcsBundle.message
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.update.AbstractCommonUpdateAction
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.layout.*
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.VcsExecutablePathSelector
import com.intellij.vcs.log.VcsLogFilterCollection.STRUCTURE_FILTER
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl
import com.intellij.vcs.log.ui.filter.StructureFilterPopupComponent
import com.intellij.vcs.log.ui.filter.VcsLogClassicFilterUi
import git4idea.GitVcs
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepositoryManager
import git4idea.update.GitUpdateProjectInfoLogProperties
import git4idea.update.getUpdateMethods
import org.jetbrains.annotations.CalledInAny
import java.awt.Color
import java.util.function.Consumer
import javax.swing.JLabel

private fun projectSettings(project: Project) = GitVcsSettings.getInstance(project)
private val applicationSettings get() = GitVcsApplicationSettings.getInstance()
private val gitOptionGroupName get() = message("settings.git.option.group")

// @formatter:off
private fun cdSyncBranches(project: Project)                                  = CheckboxDescriptor(message("sync.setting"), PropertyBinding({ projectSettings(project).syncSetting == DvcsSyncSettings.Value.SYNC }, { projectSettings(project).syncSetting = if (it) DvcsSyncSettings.Value.SYNC else DvcsSyncSettings.Value.DONT_SYNC }), groupName = gitOptionGroupName)
private val cdCommitOnCherryPick                                        get() = CheckboxDescriptor(message("settings.commit.automatically.on.cherry.pick"), PropertyBinding(applicationSettings::isAutoCommitOnCherryPick, applicationSettings::setAutoCommitOnCherryPick), groupName = gitOptionGroupName)
private fun cdAddCherryPickSuffix(project: Project)                           = CheckboxDescriptor(message("settings.add.suffix"), PropertyBinding({ projectSettings(project).shouldAddSuffixToCherryPicksOfPublishedCommits() }, { projectSettings(project).setAddSuffixToCherryPicks(it) }), groupName = gitOptionGroupName)
private fun cdWarnAboutCrlf(project: Project)                                 = CheckboxDescriptor(message("settings.crlf"), PropertyBinding({ projectSettings(project).warnAboutCrlf() }, { projectSettings(project).setWarnAboutCrlf(it) }), groupName = gitOptionGroupName)
private fun cdWarnAboutDetachedHead(project: Project)                         = CheckboxDescriptor(message("settings.detached.head"), PropertyBinding({ projectSettings(project).warnAboutDetachedHead() }, { projectSettings(project).setWarnAboutDetachedHead(it) }), groupName = gitOptionGroupName)
private fun cdAutoUpdateOnPush(project: Project)                              = CheckboxDescriptor(message("settings.auto.update.on.push.rejected"), PropertyBinding({ projectSettings(project).autoUpdateIfPushRejected() }, { projectSettings(project).setAutoUpdateIfPushRejected(it) }), groupName = gitOptionGroupName)
private fun cdShowCommitAndPushDialog(project: Project)                       = CheckboxDescriptor(message("settings.push.dialog"), PropertyBinding({ projectSettings(project).shouldPreviewPushOnCommitAndPush() }, { projectSettings(project).setPreviewPushOnCommitAndPush(it) }), groupName = gitOptionGroupName)
private fun cdHidePushDialogForNonProtectedBranches(project: Project)         = CheckboxDescriptor(message("settings.push.dialog.for.protected.branches"), PropertyBinding({ projectSettings(project).isPreviewPushProtectedOnly }, { projectSettings(project).isPreviewPushProtectedOnly = it }), groupName = gitOptionGroupName)
private val cdOverrideCredentialHelper                                  get() = CheckboxDescriptor(message("settings.credential.helper"), PropertyBinding({ applicationSettings.isUseCredentialHelper }, { applicationSettings.isUseCredentialHelper = it }), groupName = gitOptionGroupName)
// @formatter:on

internal fun gitOptionDescriptors(project: Project): List<OptionDescription> {
  val list = mutableListOf(
    cdCommitOnCherryPick,
    cdAutoUpdateOnPush(project),
    cdWarnAboutCrlf(project),
    cdWarnAboutDetachedHead(project)
  )
  val manager = GitRepositoryManager.getInstance(project)
  if (manager.moreThanOneRoot()) {
    list += cdSyncBranches(project)
  }
  return list.map(CheckboxDescriptor::asOptionDescriptor)
}

internal class GitVcsPanel(private val project: Project) :
  BoundConfigurable(GitVcs.NAME, "project.propVCSSupport.VCSs.Git"),
  SearchableConfigurable {

  private val projectSettings by lazy { GitVcsSettings.getInstance(project) }

  @Volatile
  private var versionCheckRequested = false

  private val currentUpdateInfoFilterProperties = MyLogProperties(project.service<GitUpdateProjectInfoLogProperties>())

  private lateinit var branchUpdateInfoRow: Row
  private lateinit var branchUpdateInfoCommentRow: Row
  private lateinit var supportedBranchUpLabel: JLabel

  private val pathSelector: VcsExecutablePathSelector by lazy {
    VcsExecutablePathSelector("Git", disposable!!, Consumer { path -> testExecutable(path) })
  }

  private fun testExecutable(pathToGit: String) {
    val modalityState = ModalityState.stateForComponent(pathSelector.mainPanel)
    val errorNotifier = InlineErrorNotifierFromSettings(
      GitExecutableInlineComponent(pathSelector.errorComponent, modalityState, null),
      modalityState, disposable!!
    )

    object : Task.Modal(project, GitBundle.getString("git.executable.version.progress.title"), true) {
      private lateinit var gitVersion: GitVersion

      override fun run(indicator: ProgressIndicator) {
        val executableManager = GitExecutableManager.getInstance()
        val executable = executableManager.getExecutable(pathToGit)
        executableManager.dropVersionCache(executable)
        gitVersion = executableManager.identifyVersion(executable)
      }

      override fun onThrowable(error: Throwable) {
        val problemHandler = findGitExecutableProblemHandler(project)
        problemHandler.showError(error, errorNotifier)
      }

      override fun onSuccess() {
        if (gitVersion.isSupported) {
          errorNotifier.showMessage(GitBundle.message("git.executable.version.is", gitVersion.presentation))
        }
        else {
          showUnsupportedVersionError(project, gitVersion, errorNotifier)
        }
      }
    }.queue()
  }

  private inner class InlineErrorNotifierFromSettings(inlineComponent: InlineComponent,
                                                      private val modalityState: ModalityState,
                                                      disposable: Disposable) :
    InlineErrorNotifier(inlineComponent, modalityState, disposable) {
    @CalledInAny
    override fun showError(text: String, description: String?, fixOption: ErrorNotifier.FixOption?) {
      if (fixOption is ErrorNotifier.FixOption.Configure) {
        super.showError(text, description, null)
      }
      else {
        super.showError(text, description, fixOption)
      }
    }

    override fun resetGitExecutable() {
      super.resetGitExecutable()
      GitExecutableManager.getInstance().getDetectedExecutable(project) // populate cache
      invokeAndWaitIfNeeded(modalityState) {
        resetPathSelector()
      }
    }
  }

  private fun getCurrentExecutablePath(): String? = pathSelector.currentPath?.takeIf { it.isNotBlank() }

  private fun LayoutBuilder.gitExecutableRow() = row {
    pathSelector.mainPanel(growX)
      .onReset {
        resetPathSelector()
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
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
      }
  }

  private fun resetPathSelector() {
    val projectSettingsPathToGit = projectSettings.pathToGit
    val detectedExecutable = try {
      GitExecutableManager.getInstance().getDetectedExecutable(project)
    }
    catch (e: ProcessCanceledException) {
      GitExecutableDetector.getDefaultExecutable()
    }
    pathSelector.reset(applicationSettings.savedPathToGit,
                       projectSettingsPathToGit != null,
                       projectSettingsPathToGit,
                       detectedExecutable)
    updateBranchUpdateInfoRow()
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
              GitExecutableManager.getInstance().testGitExecutableVersionValid(project)
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
      supportedBranchUpLabel = JBLabel(message("settings.supported.for.2.9"))
      cell {
        label(message("settings.explicitly.check"))
        comboBox(
          EnumComboBoxModel(GitIncomingCheckStrategy::class.java),
          {
            projectSettings.incomingCheckStrategy
          },
          { selectedStrategy ->
            projectSettings.incomingCheckStrategy = selectedStrategy as GitIncomingCheckStrategy
            updateBranchUpdateInfoRow()
            if (!project.isDefault) {
              GitBranchIncomingOutgoingManager.getInstance(project).updateIncomingScheduling()
            }
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
        checkBox(cdSyncBranches(project)).applyToComponent {
          toolTipText = message("sync.setting.description", "Git")
        }
      }
    }
    row {
      checkBox(cdCommitOnCherryPick)
    }
    row {
      checkBox(cdAddCherryPickSuffix(project))
    }
    row {
      checkBox(cdWarnAboutCrlf(project))
    }
    row {
      checkBox(cdWarnAboutDetachedHead(project))
    }
    branchUpdateInfoRow()
    row {
      cell {
        label(message("settings.update.method"))
        comboBox(
          CollectionComboBoxModel(getUpdateMethods()),
          { projectSettings.updateMethod },
          { projectSettings.updateMethod = it!! },
          renderer = SimpleListCellRenderer.create<UpdateMethod>("", UpdateMethod::getName)
        )
      }
    }
    row {
      cell {
        label(message("settings.clean.working.tree"))
        buttonGroup({ projectSettings.saveChangesPolicy }, { projectSettings.saveChangesPolicy = it }) {
          GitSaveChangesPolicy.values().forEach { saveSetting ->
            radioButton(saveSetting.name.toLowerCase().capitalize(), saveSetting)
          }
        }
      }
    }
    row {
      checkBox(cdAutoUpdateOnPush(project))
    }
    row {
      val previewPushOnCommitAndPush = checkBox(cdShowCommitAndPushDialog(project))
      row {
        checkBox(cdHidePushDialogForNonProtectedBranches(project))
          .enableIf(previewPushOnCommitAndPush.selected)
      }
    }
    row {
      cell {
        label(message("settings.protected.branched"))
        val protectedBranchesField = ExpandableTextField(ParametersListUtil.COLON_LINE_PARSER, ParametersListUtil.COLON_LINE_JOINER)
        val sharedSettings = GitSharedSettings.getInstance(project)
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
      checkBox(cdOverrideCredentialHelper)
    }

    if (AbstractCommonUpdateAction.showsCustomNotification(listOf(GitVcs.getInstance(project)))) {
      updateProjectInfoFilter()
    }
  }

  private fun LayoutBuilder.updateProjectInfoFilter() {
    row {
      cell {
        val storedProperties = project.service<GitUpdateProjectInfoLogProperties>()
        val roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(GitVcs.getInstance(project)).toSet()
        val model = VcsLogClassicFilterUi.FileFilterModel(roots, currentUpdateInfoFilterProperties, null)
        val component = object : StructureFilterPopupComponent(currentUpdateInfoFilterProperties, model, VcsLogColorManagerImpl(roots)) {
          override fun shouldDrawLabel(): Boolean = false
          override fun shouldIndicateHovering(): Boolean = false
          override fun getDefaultSelectorForeground(): Color = UIUtil.getLabelForeground()
        }.initUi()

        label(message("settings.filter.update.info"))

        component()
          .onIsModified {
            storedProperties.getFilterValues(STRUCTURE_FILTER.name) != currentUpdateInfoFilterProperties.structureFilter
          }
          .onApply {
            storedProperties.saveFilterValues(STRUCTURE_FILTER.name, currentUpdateInfoFilterProperties.structureFilter)
          }
          .onReset {
            currentUpdateInfoFilterProperties.structureFilter = storedProperties.getFilterValues(STRUCTURE_FILTER.name)
            model.updateFilterFromProperties()
          }
      }
    }
  }

  private class MyLogProperties(mainProperties: GitUpdateProjectInfoLogProperties) : MainVcsLogUiProperties by mainProperties {
    var structureFilter: List<String>? = null

    override fun getFilterValues(filterName: String): List<String>? = structureFilter.takeIf { filterName == STRUCTURE_FILTER.name }

    override fun saveFilterValues(filterName: String, values: MutableList<String>?) {
      if (filterName == STRUCTURE_FILTER.name) {
        structureFilter = values
      }
    }
  }
}