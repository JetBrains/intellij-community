// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commit

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.text.CharArrayUtil
import git4idea.GitUtil
import git4idea.history.GitLogUtil
import git4idea.repo.GitRepository

class GitCommitCompletionContributor : CompletionContributor(), DumbAware {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val file = parameters.originalFile
    val project = file.project
    if (!isCommitMessageFile(project, file)) return

    val completionPrefix = file.text.take(parameters.offset) // match from the start of the document only
    gitPrefixes
      .filter { prefix -> completionPrefix.startsWith(prefix.prefixToMatch) }
      .forEach { prefix ->
        lastCommitsCompletionWithPrefix(project, result, completionPrefix, prefix.value)
      }
  }

  private fun lastCommitsCompletionWithPrefix(project: Project,
                                              result: CompletionResultSet,
                                              completionPrefix: String,
                                              gitPrefix: String) {
    if (Registry.`is`(COMPLETION_REGISTRY_KEY)) {
      val repository = GitUtil.getRepositories(project).singleOrNull() ?: return
      result.caseInsensitive()
        .withPrefixMatcher(PlainPrefixMatcher(completionPrefix, true))
        .addAllElements(
          getLastCommits(repository).reversed().mapIndexed { i, oldCommitMessage ->
            PrioritizedLookupElement.withPriority(LookupElementBuilder.create("$gitPrefix $oldCommitMessage"), i.toDouble())
          }
        )
    }
  }

  private fun getLastCommits(repository: GitRepository, n: Int = 20): List<String> {
    val future = ApplicationManager.getApplication().executeOnPooledThread<List<String>> {
      GitLogUtil.collectMetadata(repository.project, repository.root, "-n $n").commits.map { it.subject }
    }
    return ApplicationUtil.runWithCheckCanceled(future, ProgressIndicatorProvider.getInstance().progressIndicator)
  }

  data class GitPrefix(val value: String, val prefixToMatch: String)
}

class GitCommitCompletionCharFilter : CharFilter() {
  override fun acceptChar(c: Char, prefixLength: Int, lookup: Lookup): Result? {
    if (c != COMMAND_END && c != ' ') return null

    val file = lookup.psiFile
    if (file == null || !isCommitMessageFile(lookup.project, file)) return null


    val currentItem = lookup.currentItem ?: return null
    if (matchesAfterAppendingChar(lookup, currentItem, c)) {
      return Result.ADD_TO_PREFIX
    }

    return null
  }

  private fun matchesAfterAppendingChar(lookup: Lookup, item: LookupElement, c: Char): Boolean {
    val matcher = lookup.itemMatcher(item)
    return matcher.cloneWithPrefix(matcher.prefix + (lookup as LookupImpl).additionalPrefix + c).prefixMatches(item)
  }
}

/**
 * Opens the completion popup when the user ends an autosquash command, such as `fixup!`.
 *
 * The platform starts the popup only for a letter, a digit or `_`.
 * See `CompletionAutoPopupHandler`.
 * The [COMMAND_END] character needs this handler.
 *
 * The handler stays with [GitCommitCompletionContributor], which needs the Git API of the backend module.
 */
@Suppress("SplitModeApiUsage")
internal class GitCommitCompletionTypedHandler : TypedHandlerDelegate() {
  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (charTyped != COMMAND_END) return Result.CONTINUE
    // the char filter keeps an open popup, so do not restart the session
    if (LookupManager.getActiveLookup(editor) != null) return Result.CONTINUE
    if (editor.selectionModel.hasSelection()) return Result.CONTINUE
    if (!Registry.`is`(COMPLETION_REGISTRY_KEY)) return Result.CONTINUE
    // the document also tells that this editor is not an injected one
    if (!CommitMessage.isCommitMessage(editor.document)) return Result.CONTINUE

    // the typed char is not in the document yet, and the contributor matches from the start of the document only
    if (!isAutosquashCommand(editor.document.immutableCharSequence, editor.caretModel.offset)) return Result.CONTINUE

    AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
    return Result.CONTINUE
  }
}

private const val COMPLETION_REGISTRY_KEY = "git.commit.completion.fixup.squash"

private const val COMMAND_END = '!'

private val gitPrefixes = listOf(
  GitCommitCompletionContributor.GitPrefix("fixup!", "fixu"),
  GitCommitCompletionContributor.GitPrefix("squash!", "squ"),
  GitCommitCompletionContributor.GitPrefix("amend!", "amen")
)

/**
 * Tells if the first [length] characters of [text], plus [COMMAND_END], make an autosquash command.
 *
 * Every command ends with [COMMAND_END], so the command is one character longer than the region.
 */
private fun isAutosquashCommand(text: CharSequence, length: Int): Boolean = gitPrefixes.any { prefix ->
  val command = prefix.value
  command.length == length + 1 && CharArrayUtil.regionMatches(text, 0, length, command, 0, length)
}

private fun isCommitMessageFile(project: Project, file: PsiFile): Boolean {
  val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return false
  return CommitMessage.isCommitMessage(document)
}
