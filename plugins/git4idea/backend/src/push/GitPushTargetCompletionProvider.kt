// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.util.textCompletion.DefaultTextCompletionValueDescriptor
import com.intellij.util.textCompletion.TextCompletionProviderBase
import git4idea.GitRemoteBranch
import git4idea.config.GitPushTargetHistoryEntry
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import java.util.function.Supplier
import javax.swing.Icon

/**
 * One suggestion for the push target field: a recent push target or an existing remote branch.
 * The [order] is the position in the popup: the sorter uses it instead of the default relevance.
 */
internal class GitPushTargetVariant(
  val branchName: String,
  val isRecent: Boolean,
  val order: Int,
)

/**
 * Supplies the suggestions for the push target field: the recent push targets first,
 * then the existing remote branches. The provider reads the recent targets on each
 * completion session, so a new push updates the next popup.
 */
internal class GitPushTargetCompletionProvider(
  private val repository: GitRepository,
  private val source: GitPushSource,
  private val currentRemoteName: Supplier<String?>,
) : TextCompletionProviderBase<GitPushTargetVariant>(GitPushTargetVariantDescriptor(), emptyList(), false), DumbAware {

  override fun getValues(
    parameters: CompletionParameters,
    prefix: String,
    result: CompletionResultSet,
  ): Collection<GitPushTargetVariant> {
    return getVariants(getRecentEntries(), currentRemoteName.get(), getRemoteBranchNames(repository))
  }

  override fun applyPrefixMatcher(result: CompletionResultSet, prefix: String): CompletionResultSet {
    // The default sorter reorders the items by the completion statistics: the last chosen item wins.
    // Replace it with a sorter that keeps the provider order: the recent targets first.
    return super.applyPrefixMatcher(result, prefix)
      .withRelevanceSorter(CompletionSorter.emptySorter().weigh(GitPushTargetVariantWeigher))
  }

  private fun getRecentEntries(): List<GitPushTargetHistoryEntry> {
    val sourceBranch = source.branch?.name ?: return emptyList()
    return GitVcsSettings.getInstance(repository.project).getRecentPushTargets(repository.root.path, sourceBranch)
  }

  companion object {
    private val REMOTE_BRANCH_COMPARATOR = Comparator<GitRemoteBranch> { o1, o2 ->
      val remoteName1 = o1.remote.name
      val remoteName2 = o2.remote.name
      when {
        remoteName1 == remoteName2 -> o1.nameForLocalOperations.compareTo(o2.nameForLocalOperations)
        remoteName1 == GitRemote.ORIGIN -> -1
        remoteName2 == GitRemote.ORIGIN -> 1
        else -> remoteName1.compareTo(remoteName2)
      }
    }

    private fun getRemoteBranchNames(repository: GitRepository): List<String> {
      return repository.branches.remoteBranches
        .sortedWith(REMOTE_BRANCH_COMPARATOR)
        .map { it.nameForRemoteOperations }
    }

    @VisibleForTesting
    fun getVariants(
      recentEntries: List<GitPushTargetHistoryEntry>,
      currentRemote: String?,
      remoteBranchNames: List<String>,
    ): List<GitPushTargetVariant> {
      val variants = ArrayList<GitPushTargetVariant>()
      for (entry in recentEntries) {
        if (entry.targetRemote == null || entry.targetRemote != currentRemote) continue
        val branchName = entry.targetBranch ?: continue
        variants.add(GitPushTargetVariant(branchName, true, variants.size))
      }
      val recentNames = variants.mapTo(HashSet()) { it.branchName }
      for (name in remoteBranchNames) {
        if (name in recentNames) continue
        variants.add(GitPushTargetVariant(name, false, variants.size))
      }
      return variants
    }
  }
}

/**
 * Sorts the popup by the provider order and keeps the platform relevance out.
 * The completion statistics then cannot move the last chosen item to the top.
 */
private object GitPushTargetVariantWeigher : LookupElementWeigher("gitPushTargetVariantOrder") {
  override fun weigh(element: LookupElement): Comparable<*> {
    val variant = element.`object` as? GitPushTargetVariant ?: return Int.MAX_VALUE
    return variant.order
  }
}

private class GitPushTargetVariantDescriptor : DefaultTextCompletionValueDescriptor<GitPushTargetVariant>() {
  override fun getLookupString(item: GitPushTargetVariant): String = item.branchName

  override fun getIcon(item: GitPushTargetVariant): Icon? = if (item.isRecent) AllIcons.Vcs.History else null

  override fun getTypeText(item: GitPushTargetVariant): @Nls String? =
    if (item.isRecent) GitBundle.message("push.dialog.target.completion.recently.pushed") else null

  // Keep the supplied order: the recent targets first, then the sorted remote branches.
  override fun compare(item1: GitPushTargetVariant, item2: GitPushTargetVariant): Int = 0
}
