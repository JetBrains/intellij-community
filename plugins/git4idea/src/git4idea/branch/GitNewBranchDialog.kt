// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.isSyncOptionEnabled
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.util.whenDocumentChanged
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.WHEN_DOCUMENT_CHANGED
import com.intellij.openapi.ui.validation.WHEN_STATE_CHANGED
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.textCompletion.DefaultTextCompletionValueDescriptor
import com.intellij.util.textCompletion.TextCompletionProviderBase
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.JBUI
import com.intellij.vcs.git.ui.GitBranchesTreeIconProvider
import git4idea.GitBranchesUsageCollector.branchDialogRepositoryManuallySelected
import git4idea.branch.GitBranchOperationType.CHECKOUT
import git4idea.branch.GitBranchOperationType.CREATE
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.validators.*
import org.jetbrains.annotations.Nls
import javax.swing.JCheckBox

data class GitNewBranchOptions @JvmOverloads constructor(
  val name: String,
  @get:JvmName("shouldCheckout") val checkout: Boolean = true,
  @get:JvmName("shouldReset") val reset: Boolean = false,
  @get:JvmName("shouldSetTracking") val setTracking: Boolean = false,
  @get:JvmName("repositories") val repositories: Collection<GitRepository> = emptyList(),
  @get:JvmName("shouldUnsetUpstream") val unsetUpstream: Boolean = false,
)


enum class GitBranchOperationType(@Nls val text: String, @Nls val description: String = "") {
  CREATE(GitBundle.message("new.branch.dialog.operation.create.name"),
         GitBundle.message("new.branch.dialog.operation.create.description")),
  CHECKOUT(GitBundle.message("new.branch.dialog.operation.checkout.name"),
           GitBundle.message("new.branch.dialog.operation.checkout.description")),
  RENAME(GitBundle.message("new.branch.dialog.operation.rename.name"))
}

internal class GitNewBranchDialog @JvmOverloads constructor(
  private val project: Project,
  private var repositories: Collection<GitRepository>,
  @NlsContexts.DialogTitle dialogTitle: String,
  initialName: String?,
  private val showCheckOutOption: Boolean = true,
  private val showResetOption: Boolean = false,
  private val showSetTrackingOption: Boolean = false,
  private val showUnsetUpstreamOption: Boolean = false,
  private val localConflictsAllowed: Boolean = false,
  private val operation: GitBranchOperationType = if (showCheckOutOption) CREATE else CHECKOUT,
) : DialogWrapper(project, true) {

  companion object {
    private const val NAME_SEPARATOR = '/'

    private val ALL_REPOSITORIES: GitRepository? = null
  }

  private var checkout = true
  private var reset = false
  private var tracking = showSetTrackingOption
  private var unsetUpstream = false
  private var branchName = initialName.orEmpty()
  private val validator = GitRefNameValidator.getInstance()

  private val localBranchDirectories = collectDirectories(collectLocalBranchNames().asIterable(), false).toSet()

  private val allRepositories = GitRepositoryManager.getInstance(project).repositories
  private val initialRepositories = repositories.toList()

  private val warningVisibilityProperty = AtomicBooleanProperty(false)
  private var repositoryManuallySelected = false

  init {
    title = dialogTitle
    setOKButtonText(operation.text)
    init()
  }

  fun showAndGetOptions(): GitNewBranchOptions? {
    if (!showAndGet()) return null
    return GitNewBranchOptions(validator.cleanUpBranchName(branchName).trim(), checkout, reset, tracking, repositories, unsetUpstream)
  }

  override fun createCenterPanel() = panel {
    val repositoriesComboBox = createRepositoriesCombobox()
    val overwriteCheckbox = JCheckBox(GitBundle.message("new.branch.dialog.overwrite.existing.branch.checkbox"))
    val branchNameField = TextFieldWithCompletion(project, createBranchNameCompletion(), branchName,
                                                  /*oneLineMode*/ true,
                                                  /*autoPopup*/ true,
                                                  /*forceAutoPopup*/ false,
                                                  /*showHint*/ false)
      .apply {
        minimumSize = JBUI.size(240, 0)
        setupCleanBranchNameAndAdjustCursorIfNeeded()
      }
    row(GitBundle.message("new.branch.dialog.branch.name")) {
      cell(branchNameField)
        .bind({ c -> c.text }, { c, v -> c.text = v }, ::branchName.toMutableProperty())
        .align(AlignX.FILL)
        .focused()
        .applyToComponent {
          selectAll()
        }
        .validationRequestor(WHEN_STATE_CHANGED(overwriteCheckbox))
        .validationRequestor(WHEN_STATE_CHANGED(repositoriesComboBox))
        .validationRequestor(WHEN_DOCUMENT_CHANGED)
        .validationOnApply(validateBranchName(true, overwriteCheckbox, repositoriesComboBox))
    }
    row(GitBundle.message("new.branch.dialog.branch.root.name")) {
      cell(repositoriesComboBox)
        .align(AlignX.FILL)
        .bindItem({ if (repositories.containsAll(allRepositories)) ALL_REPOSITORIES else repositories.firstOrNull() },
                  { repository ->
                    when (repository) {
                      ALL_REPOSITORIES -> repositories = allRepositories
                      else -> repositories = listOf(repository!!)
                    }
                  })
        .whenItemChangedFromUi { repository ->
          warningVisibilityProperty.set(
            GitVcsSettings.getInstance(project).isSyncOptionEnabled()
            && repository != ALL_REPOSITORIES
            && initialRepositories == allRepositories
          )
          repositoryManuallySelected =
            if (repository == ALL_REPOSITORIES) initialRepositories != allRepositories else initialRepositories.singleOrNull() != repository
        }
    }.visible(allRepositories.size > 1 && operation != GitBranchOperationType.RENAME)
    row("") { //align all cells to the right
      if (showCheckOutOption) {
        checkBox(GitBundle.message("new.branch.dialog.checkout.branch.checkbox"))
          .bindSelected(::checkout)
      }
      if (showResetOption) {
        cell(overwriteCheckbox)
          .bindSelected(::reset)
          .enabledIf(HasConflictingLocalBranchPredicate(branchNameField))
          .component
      }
      if (showSetTrackingOption) {
        checkBox(GitBundle.message("new.branch.dialog.set.tracking.branch.checkbox"))
          .bindSelected(::tracking)
          .component
      }
      if (showUnsetUpstreamOption) {
        checkBox(GitBundle.message("new.branch.dialog.unset.upstream.branch.checkbox"))
          .bindSelected(::unsetUpstream)
          .component
      }
    }
    row("") { //align all cells to the right
      icon(AllIcons.General.Warning).gap(RightGap.SMALL).align(AlignY.TOP)
      text(GitBundle.message("new.branch.dialog.branch.root.all.override.warning"), maxLineLength = 50)
    }
      .visibleIf(warningVisibilityProperty)

    onApply {
      if (repositoryManuallySelected) {
        branchDialogRepositoryManuallySelected()
      }
    }
  }

  private fun createRepositoriesCombobox(): ComboBox<GitRepository?> {
    val items = listOf(ALL_REPOSITORIES, *allRepositories.toTypedArray())

    return ComboBox(CollectionComboBoxModel(items)).apply {
      renderer = listCellRenderer<GitRepository?>(GitBundle.message("new.branch.dialog.branch.root.all.name")) {
        val repo = value
        if (repo == ALL_REPOSITORIES) {
          icon(AllIcons.Empty)
        }
        else if (repo != null) {
          icon(GitBranchesTreeIconProvider.forRepository(project, repo.rpcId))
          text(DvcsUtil.getShortRepositoryName(repo))
        }
      }
      setSwingPopup(false)
      SpeedSearch().installSupplyTo(this, false)
    }
  }

  private fun createBranchNameCompletion(): BranchNamesCompletion {
    val localBranches = collectLocalBranchNames()
    val remoteBranches = collectRemoteBranchNames()
    val localDirectories = collectDirectories(localBranches.asIterable(), true)
    val remoteDirectories = collectDirectories(remoteBranches.asIterable(), true)

    val allSuggestions = mutableSetOf<String>()
    allSuggestions += localBranches
    allSuggestions += remoteBranches
    allSuggestions += localDirectories
    allSuggestions += remoteDirectories
    return BranchNamesCompletion(localDirectories.toList(), allSuggestions.toList())
  }

  private fun collectLocalBranchNames() = repositories.asSequence().flatMap { it.branches.localBranches }.map { it.name }
  private fun collectRemoteBranchNames() = repositories.asSequence().flatMap { it.branches.remoteBranches }.map { it.nameForRemoteOperations }

  private fun collectDirectories(branchNames: Iterable<String>, withTrailingSlash: Boolean): Collection<String> {
    val directories = mutableSetOf<String>()
    for (branchName in branchNames) {
      if (branchName.contains(NAME_SEPARATOR)) {
        var index = 0
        while (index < branchName.length) {
          val end = branchName.indexOf(NAME_SEPARATOR, index)
          if (end == -1) break
          directories += if (withTrailingSlash) branchName.substring(0, end + 1) else branchName.substring(0, end)
          index = end + 1
        }
      }
    }
    return directories
  }

  private fun validateBranchName(onApply: Boolean, overwriteCheckbox: JCheckBox, repositoriesComboBox: ComboBox<GitRepository?>)
    : ValidationInfoBuilder.(TextFieldWithCompletion) -> ValidationInfo? = {

    val selectedRepositories = (repositoriesComboBox.selectedItem as GitRepository?)
                                                            ?.let(::listOf) ?: allRepositories

    val branchName = validator.cleanUpBranchName(it.text).trim()
    val errorInfo = (if (onApply) checkRefNameEmptyOrHead(branchName) else null)
                    ?: conflictsWithRemoteBranch(selectedRepositories, branchName)
                    ?: conflictsWithLocalBranchDirectory(localBranchDirectories, branchName)
    if (errorInfo != null) error(errorInfo.message)
    else {
      val localBranchConflict = conflictsWithLocalBranch(selectedRepositories, branchName)

      if (localBranchConflict == null || overwriteCheckbox.isSelected == true) null // no conflicts or ask to reset
      else if (localBranchConflict.warning && localConflictsAllowed) {
        warning(HtmlBuilder().append(localBranchConflict.message + ".").br().append(operation.description).toString())
      }
      else if (showResetOption) {
        error(HtmlBuilder().append(localBranchConflict.message + ".").br()
                .append(GitBundle.message("new.branch.dialog.overwrite.existing.branch.warning")).toString())
      }
      else error(localBranchConflict.message)
    }
  }

  private fun TextFieldWithCompletion.setupCleanBranchNameAndAdjustCursorIfNeeded() {
    whenDocumentChanged(disposable) {
      // Do not change Document inside DocumentListener callback
      invokeLater {
        cleanBranchNameAndAdjustCursorIfNeeded()
      }
    }
  }

  private fun TextFieldWithCompletion.cleanBranchNameAndAdjustCursorIfNeeded() {
    if (isDisposed) return

    val initialText = text
    val initialCaret = caretModel.offset

    val fixedText = validator.cleanUpBranchNameOnTyping(initialText)

    // if the text didn't change, there's no point in updating it or cursorPosition
    if (fixedText == initialText) return

    val initialTextBeforeCaret = initialText.take(initialCaret)
    val fixedTextBeforeCaret = validator.cleanUpBranchNameOnTyping(initialTextBeforeCaret)

    val fixedCaret = fixedTextBeforeCaret.length

    text = fixedText
    caretModel.moveToOffset(fixedCaret)
  }

  private class BranchNamesCompletion(
    val localDirectories: List<String>,
    val allSuggestions: List<String>)
    : TextCompletionProviderBase<String>(
    DefaultTextCompletionValueDescriptor.StringValueDescriptor(),
    emptyList(),
    false
  ), DumbAware {
    override fun getValues(parameters: CompletionParameters, prefix: String, result: CompletionResultSet): Collection<String> {
      if (parameters.isAutoPopup) {
        return localDirectories
      }
      else {
        return allSuggestions
      }
    }
  }

  private inner class HasConflictingLocalBranchPredicate(val branchNameField: EditorTextField) : ComponentPredicate() {
    override fun addListener(listener: (Boolean) -> Unit) {
      branchNameField.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          listener(invoke())
        }
      })
    }

    override fun invoke(): Boolean {
      val branchName = validator.cleanUpBranchName(branchNameField.text).trim()
      val localBranchConflict = conflictsWithLocalBranch(repositories, branchName)
      return localBranchConflict != null
    }
  }
}
