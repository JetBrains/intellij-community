// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.*
import com.intellij.util.textCompletion.DefaultTextCompletionValueDescriptor
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.textCompletion.ValuesCompletionProvider
import git4idea.branch.GitBranchOperationType.CHECKOUT
import git4idea.branch.GitBranchOperationType.CREATE
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.validators.GitRefNameValidator
import git4idea.validators.checkRefNameEmptyOrHead
import git4idea.validators.conflictsWithLocalBranch
import git4idea.validators.conflictsWithRemoteBranch
import org.jetbrains.annotations.Nls
import javax.swing.JCheckBox

data class GitNewBranchOptions @JvmOverloads constructor(val name: String,
                                                         @get:JvmName("shouldCheckout") val checkout: Boolean = true,
                                                         @get:JvmName("shouldReset") val reset: Boolean = false,
                                                         @get:JvmName("shouldSetTracking") val setTracking: Boolean = false)


enum class GitBranchOperationType(@Nls val text: String, @Nls val description: String = "") {
  CREATE(GitBundle.message("new.branch.dialog.operation.create.name"),
         GitBundle.message("new.branch.dialog.operation.create.description")),
  CHECKOUT(GitBundle.message("new.branch.dialog.operation.checkout.name"),
           GitBundle.message("new.branch.dialog.operation.checkout.description")),
  RENAME(GitBundle.message("new.branch.dialog.operation.rename.name"))
}

internal class GitNewBranchDialog @JvmOverloads constructor(private val project: Project,
                                                            private val repositories: Collection<GitRepository>,
                                                            @NlsContexts.DialogTitle dialogTitle: String,
                                                            initialName: String?,
                                                            private val showCheckOutOption: Boolean = true,
                                                            private val showResetOption: Boolean = false,
                                                            private val showSetTrackingOption: Boolean = false,
                                                            private val localConflictsAllowed: Boolean = false,
                                                            private val operation: GitBranchOperationType = if (showCheckOutOption) CREATE else CHECKOUT)
  : DialogWrapper(project, true) {

  companion object {
    private const val NAME_SEPARATOR = '/'
  }

  private var checkout = true
  private var reset = false
  private var tracking = showSetTrackingOption
  private var branchName = initialName.orEmpty()
  private var overwriteCheckbox: JCheckBox? = null
  private var setTrackingCheckbox: JCheckBox? = null
  private val validator = GitRefNameValidator.getInstance()

  init {
    title = dialogTitle
    setOKButtonText(operation.text)
    init()
  }

  fun showAndGetOptions(): GitNewBranchOptions? {
    if (!showAndGet()) return null
    return GitNewBranchOptions(validator.cleanUpBranchName(branchName).trim(), checkout, reset, tracking)
  }

  override fun createCenterPanel() = panel {
    row {
      cell(TextFieldWithCompletion(project, createBranchNameCompletion(), branchName, true, false, true))
        .bind({ c -> c.text }, { c, v -> c.text = v }, ::branchName.toMutableProperty())
        .align(AlignX.FILL)
        .label(GitBundle.message("new.branch.dialog.branch.name"), LabelPosition.TOP)
        .focused()
        .validationOnApply(validateBranchName())
        .apply { startTrackingValidationIfNeeded() }
    }
    row {
      if (showCheckOutOption) {
        checkBox(GitBundle.message("new.branch.dialog.checkout.branch.checkbox"))
          .bindSelected(::checkout)
      }
      if (showResetOption) {
        overwriteCheckbox = checkBox(GitBundle.message("new.branch.dialog.overwrite.existing.branch.checkbox"))
          .bindSelected(::reset)
          .applyToComponent {
            isEnabled = false
          }
          .component
      }
      if (showSetTrackingOption) {
        setTrackingCheckbox = checkBox(GitBundle.message("new.branch.dialog.set.tracking.branch.checkbox"))
          .bindSelected(::tracking)
          .component
      }
    }
  }

  private fun createBranchNameCompletion(): BranchNamesCompletion {
    val branchNames = mutableSetOf<String>()
    branchNames += collectLocalBranchNames()
    branchNames += collectRemoteBranchNames()
    val directories = collectDirectories(branchNames).filter { it !in branchNames }
    return BranchNamesCompletion(directories + branchNames.toList())
  }

  private fun collectLocalBranchNames() = repositories.asSequence().flatMap { it.branches.localBranches }.map { it.name }
  private fun collectRemoteBranchNames() = repositories.asSequence().flatMap { it.branches.remoteBranches }.map { it.nameForRemoteOperations }

  private fun collectDirectories(branchNames: Collection<String>): Collection<String> {
    val directories = mutableSetOf<String>()
    for (branchName in branchNames) {
      if (branchName.contains(NAME_SEPARATOR)) {
        var index = 0
        while (index < branchName.length) {
          val end = branchName.indexOf(NAME_SEPARATOR, index)
          if (end == -1) break
          directories += branchName.substring(0, end + 1)
          index = end + 1
        }
      }
    }
    return directories
  }

  private fun validateBranchName(): ValidationInfoBuilder.(TextFieldWithCompletion) -> ValidationInfo? = {
    it.cleanBranchNameAndAdjustCursorIfNeeded()
    val errorInfo = checkRefNameEmptyOrHead(it.text) ?: conflictsWithRemoteBranch(repositories, it.text)
    if (errorInfo != null) error(errorInfo.message)
    else {
      val localBranchConflict = conflictsWithLocalBranch(repositories, it.text)
      overwriteCheckbox?.isEnabled = localBranchConflict != null

      if (localBranchConflict == null || overwriteCheckbox?.isSelected == true) null // no conflicts or ask to reset
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

  private fun TextFieldWithCompletion.cleanBranchNameAndAdjustCursorIfNeeded() {
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

  private fun Cell<TextFieldWithCompletion>.startTrackingValidationIfNeeded() {
    if (branchName.isEmpty()) {
      component.document.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          startTrackingValidation()
          component.document.removeDocumentListener(this)
        }
      })
    }
    else {
      startTrackingValidation()
    }
  }

  private class BranchNamesCompletion(branches: List<String>) : ValuesCompletionProvider.ValuesCompletionProviderDumbAware<String>(
    DefaultTextCompletionValueDescriptor.StringValueDescriptor(),
    branches
  )
}
