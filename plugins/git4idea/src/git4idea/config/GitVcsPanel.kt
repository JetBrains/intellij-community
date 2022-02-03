// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundCompositeConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsEnvCustomizer
import com.intellij.openapi.vcs.update.AbstractCommonUpdateAction
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.*
import com.intellij.util.Function
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.commit.CommitModeManager
import com.intellij.vcs.log.VcsLogFilterCollection.STRUCTURE_FILTER
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl
import com.intellij.vcs.log.ui.filter.StructureFilterPopupComponent
import com.intellij.vcs.log.ui.filter.VcsLogClassicFilterUi
import git4idea.GitVcs
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.config.GitExecutableSelectorPanel.Companion.createGitExecutableSelectorRow
import git4idea.config.gpg.GpgSignConfigurableRow.Companion.createGpgSignRow
import git4idea.i18n.GitBundle.message
import git4idea.index.canEnableStagingArea
import git4idea.index.enableStagingArea
import git4idea.repo.GitRepositoryManager
import git4idea.update.GitUpdateProjectInfoLogProperties
import git4idea.update.getUpdateMethods
import java.awt.Color
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.border.Border

private fun gitSharedSettings(project: Project) = GitSharedSettings.getInstance(project)
private fun projectSettings(project: Project) = GitVcsSettings.getInstance(project)
private val applicationSettings get() = GitVcsApplicationSettings.getInstance()
private val gitOptionGroupName get() = message("settings.git.option.group")

// @formatter:off
private fun cdSyncBranches(project: Project)                                  = CheckboxDescriptor(DvcsBundle.message("sync.setting"), PropertyBinding({ projectSettings(project).syncSetting == DvcsSyncSettings.Value.SYNC }, { projectSettings(project).syncSetting = if (it) DvcsSyncSettings.Value.SYNC else DvcsSyncSettings.Value.DONT_SYNC }), groupName = gitOptionGroupName)
private fun cdAddCherryPickSuffix(project: Project)                           = CheckboxDescriptor(message("settings.add.suffix"), PropertyBinding({ projectSettings(project).shouldAddSuffixToCherryPicksOfPublishedCommits() }, { projectSettings(project).setAddSuffixToCherryPicks(it) }), groupName = gitOptionGroupName)
private fun cdWarnAboutCrlf(project: Project)                                 = CheckboxDescriptor(message("settings.crlf"), PropertyBinding({ projectSettings(project).warnAboutCrlf() }, { projectSettings(project).setWarnAboutCrlf(it) }), groupName = gitOptionGroupName)
private fun cdWarnAboutDetachedHead(project: Project)                         = CheckboxDescriptor(message("settings.detached.head"), PropertyBinding({ projectSettings(project).warnAboutDetachedHead() }, { projectSettings(project).setWarnAboutDetachedHead(it) }), groupName = gitOptionGroupName)
private fun cdAutoUpdateOnPush(project: Project)                              = CheckboxDescriptor(message("settings.auto.update.on.push.rejected"), PropertyBinding({ projectSettings(project).autoUpdateIfPushRejected() }, { projectSettings(project).setAutoUpdateIfPushRejected(it) }), groupName = gitOptionGroupName)
private fun cdShowCommitAndPushDialog(project: Project)                       = CheckboxDescriptor(message("settings.push.dialog"), PropertyBinding({ projectSettings(project).shouldPreviewPushOnCommitAndPush() }, { projectSettings(project).setPreviewPushOnCommitAndPush(it) }), groupName = gitOptionGroupName)
private fun cdHidePushDialogForNonProtectedBranches(project: Project)         = CheckboxDescriptor(message("settings.push.dialog.for.protected.branches"), PropertyBinding({ projectSettings(project).isPreviewPushProtectedOnly }, { projectSettings(project).isPreviewPushProtectedOnly = it }), groupName = gitOptionGroupName)
private val cdOverrideCredentialHelper                                  get() = CheckboxDescriptor(message("settings.credential.helper"), PropertyBinding({ applicationSettings.isUseCredentialHelper }, { applicationSettings.isUseCredentialHelper = it }), groupName = gitOptionGroupName)
private fun synchronizeBranchProtectionRules(project: Project)                = CheckboxDescriptor(message("settings.synchronize.branch.protection.rules"), PropertyBinding({gitSharedSettings(project).isSynchronizeBranchProtectionRules}, { gitSharedSettings(project).isSynchronizeBranchProtectionRules = it }), groupName = gitOptionGroupName, comment = message("settings.synchronize.branch.protection.rules.description"))
private val cdEnableStagingArea                                         get() = CheckboxDescriptor(message("settings.enable.staging.area"), PropertyBinding({ applicationSettings.isStagingAreaEnabled }, { enableStagingArea(it) }), groupName = gitOptionGroupName, comment = message("settings.enable.staging.area.comment"))
// @formatter:on

internal fun gitOptionDescriptors(project: Project): List<OptionDescription> {
  val list = mutableListOf(
    cdAutoUpdateOnPush(project),
    cdWarnAboutCrlf(project),
    cdWarnAboutDetachedHead(project),
    cdEnableStagingArea
  )
  val manager = GitRepositoryManager.getInstance(project)
  if (manager.moreThanOneRoot()) {
    list += cdSyncBranches(project)
  }
  return list.map(CheckboxDescriptor::asOptionDescriptor)
}

internal class GitVcsPanel(private val project: Project) :
  BoundCompositeConfigurable<UnnamedConfigurable>(message("settings.git.option.group"), "project.propVCSSupport.VCSs.Git"),
  SearchableConfigurable {

  private val projectSettings get() = GitVcsSettings.getInstance(project)

  private fun Panel.branchUpdateInfoRow() {
    row(message("settings.explicitly.check")) {
      comboBox(EnumComboBoxModel(GitIncomingCheckStrategy::class.java))
        .bindItem({
                    projectSettings.incomingCheckStrategy
                  },
                  { selectedStrategy ->
                    projectSettings.incomingCheckStrategy = selectedStrategy as GitIncomingCheckStrategy
                    if (!project.isDefault) {
                      GitBranchIncomingOutgoingManager.getInstance(project).updateIncomingScheduling()
                    }
                  })
    }.enabledIf(AdvancedSettingsPredicate("git.update.incoming.outgoing.info", disposable!!))
  }

  private fun Panel.protectedBranchesRow() {
    row(message("settings.protected.branched")) {
      val sharedSettings = gitSharedSettings(project)
      val protectedBranchesField =
        ExpandableTextFieldWithReadOnlyText(ParametersListUtil.COLON_LINE_PARSER, ParametersListUtil.COLON_LINE_JOINER)
      if (sharedSettings.isSynchronizeBranchProtectionRules) {
        protectedBranchesField.readOnlyText = ParametersListUtil.COLON_LINE_JOINER.`fun`(sharedSettings.additionalProhibitedPatterns)
      }
      cell(protectedBranchesField)
        .horizontalAlign(HorizontalAlign.FILL)
        .bind<List<String>>(
          { ParametersListUtil.COLON_LINE_PARSER.`fun`(it.text) },
          { component, value -> component.text = ParametersListUtil.COLON_LINE_JOINER.`fun`(value) },
          PropertyBinding(
            { sharedSettings.forcePushProhibitedPatterns },
            { sharedSettings.forcePushProhibitedPatterns = it })
        )
    }
    indent {
      row {
        checkBox(synchronizeBranchProtectionRules(project))
      }
    }
  }

  override fun getId() = "vcs.${GitVcs.NAME}"

  override fun createConfigurables(): List<UnnamedConfigurable> {
    return VcsEnvCustomizer.EP_NAME.extensions.mapNotNull { it.getConfigurable(project) }
  }

  override fun createPanel(): DialogPanel = panel {
    createGitExecutableSelectorRow(project, disposable!!)
    group(message("settings.commit.group.title")) {
      row {
        checkBox(cdEnableStagingArea)
          .enabledIf(StagingAreaAvailablePredicate(project, disposable!!))
      }
      row {
        checkBox(cdWarnAboutCrlf(project))
      }
      row {
        checkBox(cdWarnAboutDetachedHead(project))
      }
      row {
        checkBox(cdAddCherryPickSuffix(project))
      }
      createGpgSignRow(project, disposable!!)
    }

    group(message("settings.push.group.title")) {
      row {
        checkBox(cdAutoUpdateOnPush(project))
      }
      lateinit var previewPushOnCommitAndPush: Cell<JBCheckBox>
      row {
        previewPushOnCommitAndPush = checkBox(cdShowCommitAndPushDialog(project))
      }
      indent {
        row {
          checkBox(cdHidePushDialogForNonProtectedBranches(project))
            .enabledIf(previewPushOnCommitAndPush.selected)
        }
      }
      protectedBranchesRow()
    }

    group(message("settings.update.group.title")) {
      buttonsGroup {
        row(message("settings.update.method")) {
          getUpdateMethods().forEach { saveSetting ->
            radioButton(saveSetting.methodName, saveSetting)
          }
        }.layout(RowLayout.INDEPENDENT)
      }.bind({ projectSettings.updateMethod }, { projectSettings.updateMethod = it })
      buttonsGroup {
        row(message("settings.clean.working.tree")) {
          GitSaveChangesPolicy.values().forEach { saveSetting ->
            radioButton(saveSetting.text, saveSetting)
          }
        }.layout(RowLayout.INDEPENDENT)
      }.bind({ projectSettings.saveChangesPolicy }, { projectSettings.saveChangesPolicy = it })
      if (AbstractCommonUpdateAction.showsCustomNotification(listOf(GitVcs.getInstance(project)))) {
        updateProjectInfoFilter()
      }
    }

    if (project.isDefault || GitRepositoryManager.getInstance(project).moreThanOneRoot()) {
      row {
        checkBox(cdSyncBranches(project))
          .gap(RightGap.SMALL)
        contextHelp(DvcsBundle.message("sync.setting.description", GitVcs.DISPLAY_NAME.get()))
      }
    }
    branchUpdateInfoRow()
    row {
      checkBox(cdOverrideCredentialHelper)
    }
    for (configurable in configurables) {
      appendDslConfigurable(configurable)
    }
  }

  private fun Panel.updateProjectInfoFilter() {
    val currentUpdateInfoFilterProperties = MyLogProperties(project.service())
    row(message("settings.filter.update.info")) {
      val storedProperties = project.service<GitUpdateProjectInfoLogProperties>()
      val roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(GitVcs.getInstance(project)).toSet()
      val model = VcsLogClassicFilterUi.FileFilterModel(roots, currentUpdateInfoFilterProperties, null)
      val component = object : StructureFilterPopupComponent(currentUpdateInfoFilterProperties, model, VcsLogColorManagerImpl(roots)) {
        override fun shouldDrawLabel(): Boolean = false
        override fun shouldIndicateHovering(): Boolean = false
        override fun getDefaultSelectorForeground(): Color = UIUtil.getLabelForeground()
        override fun createUnfocusedBorder(): Border {
          return FilledRoundedBorder(JBColor.namedColor("Component.borderColor", Gray.xBF), ARC_SIZE, BORDER_SIZE, true)
        }
      }.initUi()

      cell(component)
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

private typealias ParserFunction = Function<String, List<String>>
private typealias JoinerFunction = Function<List<String>, String>

internal class ExpandableTextFieldWithReadOnlyText(lineParser: ParserFunction,
                                                   private val lineJoiner: JoinerFunction) : ExpandableTextField(lineParser, lineJoiner) {
  var readOnlyText = ""

  init {
    addFocusListener(object : FocusAdapter() {
      override fun focusLost(e: FocusEvent) {
        val myComponent = this@ExpandableTextFieldWithReadOnlyText
        if (e.component == myComponent) {
          val document = myComponent.document
          val documentText = document.getText(0, document.length)
          updateReadOnlyText(documentText)
        }
      }
    })
  }

  override fun setText(t: String?) {
    if (!t.isNullOrBlank() && t != text) {
      updateReadOnlyText(t)
    }
    super.setText(t)
  }

  private fun updateReadOnlyText(@NlsSafe text: String) {
    if (readOnlyText.isBlank()) return

    val readOnlySuffix = if (text.isBlank()) readOnlyText else lineJoiner.join("", readOnlyText) // NON-NLS
    with(emptyText as TextComponentEmptyText) {
      clear()
      appendText(text, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      appendText(readOnlySuffix, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      setTextToTriggerStatus(text) //this will force status text rendering in case if the text field is not empty
    }
  }

  fun JoinerFunction.join(vararg items: String): String = `fun`(items.toList())
}

class StagingAreaAvailablePredicate(val project: Project, val disposable: Disposable) : ComponentPredicate() {
  override fun addListener(listener: (Boolean) -> Unit) {
    project.messageBus.connect(disposable).subscribe(CommitModeManager.SETTINGS, object : CommitModeManager.SettingsListener {
      override fun settingsChanged() {
        listener(invoke())
      }
    })
  }

  override fun invoke(): Boolean = canEnableStagingArea()
}

class HasGitRootsPredicate(val project: Project, val disposable: Disposable) : ComponentPredicate() {
  override fun addListener(listener: (Boolean) -> Unit) {
    project.messageBus.connect(disposable).subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED,
                                                     VcsRepositoryMappingListener { listener(invoke()) })
  }

  override fun invoke(): Boolean = GitRepositoryManager.getInstance(project).repositories.size != 0
}

class AdvancedSettingsPredicate(val id: String, val disposable: Disposable) : ComponentPredicate() {
  override fun addListener(listener: (Boolean) -> Unit) {
    ApplicationManager.getApplication().messageBus.connect(disposable)
      .subscribe(AdvancedSettingsChangeListener.TOPIC, object : AdvancedSettingsChangeListener {
        override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
          listener(invoke())
        }
      })
  }

  override fun invoke(): Boolean = AdvancedSettings.getBoolean(id)
}
