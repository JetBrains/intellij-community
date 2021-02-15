// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible.filters

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.MultiMap
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogUserFilter
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.util.VcsUserUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class VcsLogUserFilterImpl(private val users: Collection<String>,
                                    private val data: Map<VirtualFile, VcsUser>,
                                    allUsers: Set<VcsUser>) : VcsLogUserFilter {
  private val allUsersByNames: MultiMap<String, VcsUser> = MultiMap.create()
  private val allUsersByEmails: MultiMap<String, VcsUser> = MultiMap.create()

  init {
    for (user in allUsers) {
      val name = user.name
      if (name.isNotEmpty()) {
        allUsersByNames.putValue(VcsUserUtil.getNameInStandardForm(name), user)
      }
      val email = user.email
      val nameFromEmail = VcsUserUtil.getNameFromEmail(email)
      if (nameFromEmail != null) {
        allUsersByEmails.putValue(VcsUserUtil.getNameInStandardForm(nameFromEmail), user)
      }
    }
  }

  override fun getUsers(root: VirtualFile) = users.flatMapTo(mutableSetOf()) { resolveUserName(root, it) }

  override fun getValuesAsText(): Collection<String> = users

  override fun getDisplayText(): String {
    val users = users.map { user: String ->
      if (user == VcsLogFilterObject.ME) VcsLogBundle.message("vcs.log.user.filter.me") else user
    }
    return StringUtil.join(users, ", ")
  }

  override fun matches(commit: VcsCommitMetadata): Boolean {
    return users.any { name ->
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
        else -> {
          false
        }
      }
    }
  }

  private fun resolveUserName(root: VirtualFile, name: String): Set<VcsUser> {
    if (VcsLogFilterObject.ME != name) return resolveUserName(name)

    val vcsUser = data[root]
    if (vcsUser == null) {
      LOG.warn("Can not resolve user name for root $root")
      return emptySet()
    }

    val usersByName = resolveUserName(vcsUser.name)
    val emailNamePart = VcsUserUtil.getNameFromEmail(vcsUser.email) ?: return usersByName

    val emails = usersByName.map { user -> VcsUserUtil.emailToLowerCase(user.email) }.toSet()
    val usersByEmail = resolveUserName(emailNamePart).filter { candidateUser ->
      /*
      ivan@ivanov.com and ivan@petrov.com have the same emailNamePart but they are different people
      resolveUserName("ivan") will find both of them, so we filter results here
      */
      emails.contains(VcsUserUtil.emailToLowerCase(candidateUser.email))
    }
    return usersByName + usersByEmail
  }

  private fun resolveUserName(name: String): Set<VcsUser> {
    val standardName = VcsUserUtil.getNameInStandardForm(name)
    return allUsersByNames[standardName].union(allUsersByEmails[standardName])
  }

  override fun toString(): String {
    return "author: " + StringUtil.join(users, ", ")
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val filter = other as VcsLogUserFilterImpl
    return Comparing.haveEqualElements(users, filter.users)
  }

  override fun hashCode(): Int {
    return Comparing.unorderedHashcode(users)
  }

  companion object {
    private val LOG = logger<VcsLogUserFilterImpl>()
  }
}