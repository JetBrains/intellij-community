// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible.filters

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogUserFilter
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.data.VcsLogUserResolver
import com.intellij.vcs.log.data.VcsLogUserResolverBase
import com.intellij.vcs.log.util.VcsUserUtil

/**
 * @see VcsLogFilterObject.fromUser
 * @see VcsLogFilterObject.fromUserNames
 */
internal class VcsLogUserFilterImpl(private val userNames: Collection<String>,
                                    private val resolver: VcsLogUserResolver) : VcsLogUserFilter {

  constructor(users: Collection<String>,
              currentUsers: Map<VirtualFile, VcsUser>,
              allUsers: Set<VcsUser>) : this(users, SimpleVcsLogUserResolver(currentUsers, allUsers))

  override fun getUsers(root: VirtualFile) = userNames.flatMapTo(mutableSetOf()) { resolveUserName(root, it) }

  override fun getValuesAsText(): Collection<String> = userNames

  override fun getDisplayText(): String {
    val users = userNames.map { user: String ->
      if (user == VcsLogFilterObject.ME) VcsLogBundle.message("vcs.log.user.filter.me") else user
    }
    return StringUtil.join(users, ", ")
  }

  override fun matches(commit: VcsCommitMetadata): Boolean {
    return userNames.any { name ->
      val users = resolveUserName(commit.root, name)
      when {
        users.isNotEmpty() -> {
          users.contains(commit.author)
        }
        name != VcsLogFilterObject.ME -> {
          val lowerUser = VcsUserUtil.nameToLowerCase(name)
          val result = VcsUserUtil.nameToLowerCase(commit.author.name) == lowerUser ||
                       VcsUserUtil.emailToLowerCase(commit.author.email).startsWith("$lowerUser@")
          if (result) {
            LOG.warn("Unregistered author " + commit.author + " for commit " + commit.id.asString() + "; search pattern " + name)
          }
          result
        }
        else -> false
      }
    }
  }

  private fun resolveUserName(root: VirtualFile, name: String): Set<VcsUser> {
    if (VcsLogFilterObject.ME != name) return resolver.resolveUserName(name)
    return resolver.resolveCurrentUser(root)
  }

  override fun toString(): String {
    return "author: " + StringUtil.join(userNames, ", ")
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val filter = other as VcsLogUserFilterImpl
    return Comparing.haveEqualElements(userNames, filter.userNames)
  }

  override fun hashCode(): Int {
    return Comparing.unorderedHashcode(userNames)
  }

  companion object {
    private val LOG = logger<VcsLogUserFilterImpl>()
  }
}

private class SimpleVcsLogUserResolver(override val currentUsers: Map<VirtualFile, VcsUser>,
                                       override val allUsers: Set<VcsUser>) : VcsLogUserResolverBase() {
  init {
    buildCache()
  }
}