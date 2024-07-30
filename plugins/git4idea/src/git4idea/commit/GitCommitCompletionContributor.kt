// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commit

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import git4idea.GitUtil
import git4idea.history.GitLogUtil
import git4idea.repo.GitRepository

class GitCommitCompletionContributor : CompletionContributor(), DumbAware {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val file = parameters.originalFile
    val project = file.project
    if (!isCommitMessageFile(project, file)) return

    val completionPrefix = file.text.take(parameters.offset) // match from the start of the document only
    val gitPrefixes = listOf(
      GitPrefix("fixup!", "fixu"),
      GitPrefix("squash!", "squ"),
      GitPrefix("amend!", "amen")
    )
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
    if (Registry.`is`("git.commit.completion.fixup.squash")) {
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
    if (c != '!' && c != ' ') return null

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

private fun isCommitMessageFile(project: Project, file: PsiFile): Boolean {
  val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return false
  return CommitMessage.isCommitMessage(document)
}
