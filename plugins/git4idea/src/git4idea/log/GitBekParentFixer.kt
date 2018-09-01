/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.log

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.TimedVcsCommit
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogTextFilter
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl.VcsLogFilterCollectionBuilder
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.util.BekUtil
import com.intellij.vcs.log.util.StopWatch

internal class GitBekParentFixer private constructor(private val incorrectCommits: Set<Hash>) {

  fun fixCommit(commit: TimedVcsCommit): TimedVcsCommit {
    return if (!incorrectCommits.contains(commit.id)) commit
    else object : TimedVcsCommit by commit {
      override fun getParents(): List<Hash> = ContainerUtil.reverse(commit.parents)
    }
  }

  companion object {
    @JvmStatic
    @Throws(VcsException::class)
    fun prepare(project: Project,
                root: VirtualFile,
                provider: GitLogProvider): GitBekParentFixer {
      return if (!BekUtil.isBekEnabled() || !Registry.`is`("git.log.fix.merge.commits.parents.order")) {
        GitBekParentFixer(emptySet())
      }
      else GitBekParentFixer(getIncorrectCommits(project, provider, root))
    }
  }
}

private const val MAGIC_TEXT = "Merge remote"
private val MAGIC_FILTER = createVcsLogFilterCollection()

@Throws(VcsException::class)
fun getIncorrectCommits(project: Project, provider: GitLogProvider, root: VirtualFile): Set<Hash> {
  val dataManager = VcsProjectLog.getInstance(project).dataManager
  val dataGetter = dataManager?.index?.dataGetter
  if (dataGetter == null || !dataManager.index.isIndexed(root)) {
    return getIncorrectCommitsFromProvider(provider, root)
  }
  return getIncorrectCommitsFromIndex(dataManager, dataGetter, root)
}

fun getIncorrectCommitsFromIndex(dataManager: VcsLogData,
                                 dataGetter: IndexDataGetter,
                                 root: VirtualFile): MutableSet<Hash> {
  val stopWatch = StopWatch.start("getting incorrect merges from index for ${root.name}")
  val commits = dataGetter.filter(MAGIC_FILTER.detailsFilters).asSequence()
  val result = commits.map { dataManager.storage.getCommitId(it)!! }.filter { it.root == root }.mapTo(mutableSetOf()) { it.hash }
  stopWatch.report()
  return result
}

@Throws(VcsException::class)
fun getIncorrectCommitsFromProvider(provider: GitLogProvider,
                                    root: VirtualFile): MutableSet<Hash> {
  val stopWatch = StopWatch.start("getting incorrect merges from git for ${root.name}")
  val commitsMatchingFilter = provider.getCommitsMatchingFilter(root, MAGIC_FILTER, -1)
  val result = ContainerUtil.map2Set(commitsMatchingFilter) { timedVcsCommit -> timedVcsCommit.id }
  stopWatch.report()
  return result
}

private fun createVcsLogFilterCollection(): VcsLogFilterCollection {
  val textFilter = object : VcsLogTextFilter {
    override fun matchesCase(): Boolean {
      return false
    }

    override fun isRegex(): Boolean {
      return false
    }

    override fun getText(): String {
      return MAGIC_TEXT
    }

    override fun matches(message: String): Boolean {
      return message.contains(MAGIC_TEXT)
    }
  }

  return VcsLogFilterCollectionBuilder().with(textFilter).build()
}
