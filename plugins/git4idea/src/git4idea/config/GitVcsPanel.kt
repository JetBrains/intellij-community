// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundCompositeConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_TEXT_CHANGED
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
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
import com.intellij.ui.layout.AdvancedSettingsPredicate
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.ui.layout.not
import com.intellij.util.Function
import com.intellij.util.execution.ParametersListUtil
import com.intellij.vcs.commit.CommitModeManager
import com.intellij.vcs.log.VcsLogFilterCollection.STRUCTURE_FILTER
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.ui.VcsLogColorManagerFactory
import com.intellij.vcs.log.ui.filter.FileFilterModel
import com.intellij.vcs.log.ui.filter.StructureFilterPopupComponent
import git4idea.GitVcs
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.config.GitExecutableSelectorPanel.Companion.createGitExecutableSelectorRow
import git4idea.config.gpg.GpgSignConfigurableRow.Companion.createGpgSignRow
import git4idea.i18n.GitBundle.message
import git4idea.index.canEnableStagingArea
import git4idea.index.enableStagingArea
import git4idea.repo.GitRepositoryManager
import git4idea.stash.ui.isStashTabAvailable
import git4idea.stash.ui.setStashesAndShelvesTabEnabled
import git4idea.update.GitUpdateProjectInfoLogProperties
import git4idea.update.getUpdateMethods
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.swing.border.Border

private fun gitSharedSettings(project: Project) = GitSharedSettings.getInstance(project)
private fun projectSettings(project: Project) = GitVcsSettings.getInstance(project)
private val applicationSettings get() = GitVcsApplicationSettings.getInstance()
private val gitOptionGroupName get() = message("settings.git.option.group")

// @formatter:off
private fun cdSyncBranches(project: Project)                                  = CheckboxDescriptor(DvcsBundle.message("sync.setting"), { projectSettings(project).syncSetting == DvcsSyncSettings.Value.SYNC }, { projectSettings(project).syncSetting = if (it) DvcsSyncSettings.Value.SYNC else DvcsSyncSettings.Value.DONT_SYNC }, groupName = gitOptionGroupName)
private fun cdAddCherryPickSuffix(project: Project)                           = CheckboxDescriptor(message("settings.add.suffix"), { projectSettings(project).shouldAddSuffixToCherryPicksOfPublishedCommits() }, { projectSettings(project).setAddSuffixToCherryPicks(it) }, groupName = gitOptionGroupName)
private fun cdWarnAboutCrlf(project: Project)                                 = CheckboxDescriptor(message("settings.crlf"), { projectSettings(project).warnAboutCrlf() }, { projectSettings(project).setWarnAboutCrlf(it) }, groupName = gitOptionGroupName)
private fun cdWarnAboutDetachedHead(project: Project)                         = CheckboxDescriptor(message("settings.detached.head"), { projectSettings(project).warnAboutDetachedHead() }, { projectSettings(project).setWarnAboutDetachedHead(it) }, groupName = gitOptionGroupName)
private fun cdWarnAboutLargeFiles(project: Project)                           = CheckboxDescriptor(message("settings.large.files"), { projectSettings(project).warnAboutLargeFiles() }, { projectSettings(project).setWarnAboutLargeFiles(it) }, groupName = gitOptionGroupName)
private fun cdWarnAboutBadFileNames(project: Project)                         = CheckboxDescriptor(message("settings.bad.file.names"), { projectSettings(project).warnAboutBadFileNames() }, { projectSettings(project).setWarnAboutBadFileNames(it) }, groupName = gitOptionGroupName)
private fun cdAutoUpdateOnPush(project: Project)                              = CheckboxDescriptor(message("settings.auto.update.on.push.rejected"), { projectSettings(project).autoUpdateIfPushRejected() }, { projectSettings(project).setAutoUpdateIfPushRejected(it) }, groupName = gitOptionGroupName)
private fun cdShowCommitAndPushDialog(project: Project)                       = CheckboxDescriptor(message("settings.push.dialog"), { projectSettings(project).shouldPreviewPushOnCommitAndPush() }, { projectSettings(project).setPreviewPushOnCommitAndPush(it) }, groupName = gitOptionGroupName)
private fun cdHidePushDialogForNonProtectedBranches(project: Project)         = CheckboxDescriptor(message("settings.push.dialog.for.protected.branches"), { projectSettings(project).isPreviewPushProtectedOnly }, { projectSettings(project).isPreviewPushProtectedOnly = it }, groupName = gitOptionGroupName)
private val cdOverrideCredentialHelper                                  get() = CheckboxDescriptor(message("settings.credential.helper"), { applicationSettings.isUseCredentialHelper }, { applicationSettings.isUseCredentialHelper = it }, groupName = gitOptionGroupName)
private fun synchronizeBranchProtectionRules(project: Project)                = CheckboxDescriptor(message("settings.synchronize.branch.protection.rules"), {gitSharedSettings(project).isSynchronizeBranchProtectionRules}, { gitSharedSettings(project).isSynchronizeBranchProtectionRules = it }, groupName = gitOptionGroupName, comment = message("settings.synchronize.branch.protection.rules.description"))
private val cdEnableStagingArea                                         get() = CheckboxDescriptor(message("settings.enable.staging.area"), { applicationSettings.isStagingAreaEnabled }, { enableStagingArea(it) }, groupName = gitOptionGroupName, comment = message("settings.enable.staging.area.comment"))
private val cdCombineStashesAndShelves                                  get() = CheckboxDescriptor(message("settings.enable.stashes.and.shelves"), { applicationSettings.isCombinedStashesAndShelvesTabEnabled }, { setStashesAndShelvesTabEnabled(it) }, groupName = gitOptionGroupName)
private fun cdExcludeIgnored(project: Project)                                = CheckboxDescriptor(VcsBundle.message("ignored.file.ignored.to.excluded.label"), { VcsConfiguration.getInstance(project).MARK_IGNORED_AS_EXCLUDED }, { VcsConfiguration.getInstance(project).MARK_IGNORED_AS_EXCLUDED = it }, groupName = gitOptionGroupName)
// @formatter:on

internal fun gitOptionDescriptors(project: Project): List<OptionDescription> {
  val list = mutableListOf(
    cdAutoUpdateOnPush(project),
    cdWarnAboutCrlf(project),
    cdWarnAboutDetachedHead(project),
    cdWarnAboutLargeFiles(project),
    cdWarnAboutBadFileNames(project),
    cdEnableStagingArea,
    cdExcludeIgnored(project),
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
    val predicate = AdvancedSettingsPredicate("git.update.incoming.outgoing.info", disposable!!)
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
    }.enabledIf(predicate)
    indent {
      row {
        comment(
          message("settings.explicitly.check.condition.comment", message("advanced.setting.git.update.incoming.outgoing.info")))
          .visibleIf(predicate.not())
          .applyToComponent {
            putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap(top = false))
          }
      }
    }
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
        .validationRequestor(WHEN_TEXT_CHANGED)
        .validationRequestor(DialogValidationRequestor { _, validate ->
          // when the panel shown, initiate one time validation request to right away highlight the field if needed
          validate()
        })
        .validationInfo { validateProtectedBranchesPatterns(it.text) ?: validateProtectedBranchesPatterns(it.readOnlyText) }
        .align(AlignX.FILL)
        .bind<List<String>>(
          { ParametersListUtil.COLON_LINE_PARSER.`fun`(it.text) },
          { component, value -> component.text = ParametersListUtil.COLON_LINE_JOINER.`fun`(value) },
          MutableProperty(
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

  private fun ValidationInfoBuilder.validateProtectedBranchesPatterns(text: String): ValidationInfo? {
    for (pattern in ParametersListUtil.COLON_LINE_PARSER.`fun`(text)) {
      try {
        Pattern.compile(pattern)
      }
      catch (e: PatternSyntaxException) {
        val cause = StringUtil.substringBefore(e.message.orEmpty(), "\n").orEmpty()
        return error(message("settings.protected.branched.validation", pattern, cause))
      }
    }

    return null
  }

  override fun getId() = "vcs.${GitVcs.NAME}"

  override fun createConfigurables(): List<UnnamedConfigurable> {
    return VcsEnvCustomizer.EP_NAME.extensionList.mapNotNull { it.getConfigurable(project) }
  }

  override fun createPanel(): DialogPanel = panel {
    createGitExecutableSelectorRow(project, disposable!!)

    row {
      checkBox(cdExcludeIgnored(project))
    }

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
        val checkBox = checkBox(cdWarnAboutLargeFiles(project))
          .gap(RightGap.SMALL)
        spinner(1..100000, 1)
          .bindIntValue(projectSettings::getWarnAboutLargeFilesLimitMb, projectSettings::setWarnAboutLargeFilesLimitMb)
          .enabledIf(checkBox.selected)
          .gap(RightGap.SMALL)
        label(message("settings.large.files.suffix"))
      }
      row {
        checkBox(cdWarnAboutBadFileNames(project))
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
    if (isStashTabAvailable()) {
      group(message("settings.stash")) {
        row {
          checkBox(cdCombineStashesAndShelves)
        }
        buttonsGroup(message("settings.stash.show.diff.group")) {
          row {
            radioButton(message("settings.stash.show.diff.with.local"), true)
          }
          row {
            radioButton(message("settings.stash.show.diff.with.head"), false)
          }
        }.bind({ applicationSettings.isCompareWithLocalInStashesEnabled }, { applicationSettings.isCompareWithLocalInStashesEnabled = it })
      }
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
      val model = FileFilterModel(roots, currentUpdateInfoFilterProperties, null)
      val component = object : StructureFilterPopupComponent(currentUpdateInfoFilterProperties, model,
                                                             VcsLogColorManagerFactory.create(roots)) {
        override fun shouldDrawLabel(): DrawLabelMode = DrawLabelMode.NEVER

        override fun shouldIndicateHovering(): Boolean = false

        override fun getEmptyFilterValue(): String {
          return ALL_ACTION_TEXT.get()
        }

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
