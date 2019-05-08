// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import com.intellij.dvcs.DvcsUtil.getShortNames
import com.intellij.dvcs.DvcsUtil.getShortRepositoryName
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.update.UpdateSession
import com.intellij.util.containers.MultiMap
import git4idea.repo.GitRepository

/**
 * Git update session implementation
 */
class GitUpdateSession(private val result: Boolean, private val skippedRoots: Map<GitRepository, String>) : UpdateSession {

  override fun getExceptions(): List<VcsException> {
    return emptyList()
  }

  override fun onRefreshFilesCompleted() {}

  override fun isCanceled(): Boolean {
    return !result
  }

  override fun getAdditionalNotificationContent(): String? {
    if (skippedRoots.isEmpty()) return null

    if (skippedRoots.size == 1) {
      val repo = skippedRoots.keys.iterator().next()
      return "${getShortRepositoryName(repo)} was skipped (${skippedRoots[repo]})"
    }

    val prefix = "Skipped ${skippedRoots.size} repositories: <br/>"
    val grouped = groupByReasons(skippedRoots)
    if (grouped.keySet().size == 1) {
      val reason = grouped.keySet().iterator().next()
      return prefix + getShortNames(grouped.get(reason)) + " (" + reason + ")"
    }

    return prefix + StringUtil.join(grouped.keySet(), { reason -> getShortNames(grouped.get(reason)) + " (" + reason + ")" }, "<br/>")
  }

  private fun groupByReasons(skippedRoots: Map<GitRepository, String>): MultiMap<String, GitRepository> {
    val result = MultiMap.create<String, GitRepository>()
    skippedRoots.forEach { (file, s) -> result.putValue(s, file) }
    return result
  }
}
