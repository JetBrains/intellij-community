// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible.filters

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
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

  override fun getUsers(root: VirtualFile): Collection<VcsUser> {
    val result = mutableSetOf<VcsUser>()
    for (user in users) {
      result.addAll(getUsers(root, user))
    }
    return result
  }

  private fun getUsers(root: VirtualFile, name: String): Set<VcsUser> {
    val users = mutableSetOf<VcsUser>()
    if (VcsLogFilterObject.ME == name) {
      val vcsUser = data[root]
      if (vcsUser != null) {
        users.addAll(getUsers(vcsUser.name)) // do not just add vcsUser, also add synonyms
        val emailNamePart = VcsUserUtil.getNameFromEmail(vcsUser.email)
        if (emailNamePart != null) {
          val emails = ContainerUtil.map2Set(users) { user: VcsUser -> VcsUserUtil.emailToLowerCase(user.email) }
          for (candidateUser in getUsers(emailNamePart)) {
            if (emails.contains(VcsUserUtil.emailToLowerCase(candidateUser.email))) {
              users.add(candidateUser)
            }
          }
        }
      }
      else {
        LOG.warn("Can not resolve user name for root $root")
      }
    }
    else {
      users.addAll(getUsers(name))
    }
    return users
  }

  override fun getValuesAsText(): Collection<String> {
    return users
  }

  override fun getDisplayText(): String {
    val users = ContainerUtil.map(users) { user: String ->
      val me = VcsLogBundle.message("vcs.log.user.filter.me")
      if (user == VcsLogFilterObject.ME) me else user
    }
    return StringUtil.join(users, ", ")
  }

  override fun matches(commit: VcsCommitMetadata): Boolean {
    return ContainerUtil.exists(users) { name: String ->
      val users = getUsers(commit.root, name)
      if (users.isNotEmpty()) {
        return@exists users.contains(commit.author)
      }
      else if (name != VcsLogFilterObject.ME) {
        val lowerUser = VcsUserUtil.nameToLowerCase(name)
        val result = VcsUserUtil.nameToLowerCase(commit.author.name) == lowerUser ||
                     VcsUserUtil.emailToLowerCase(commit.author.email).startsWith("$lowerUser@")
        if (result) {
          LOG.warn("Unregistered author " + commit.author + " for commit " + commit.id.asString() + "; search pattern " + name)
        }
        return@exists result
      }
      false
    }
  }

  private fun getUsers(name: String): Set<VcsUser> {
    val standardName = VcsUserUtil.getNameInStandardForm(name)
    val result = mutableSetOf<VcsUser>()
    result.addAll(allUsersByNames[standardName])
    result.addAll(allUsersByEmails[standardName])
    return result
  }

  override fun toString(): String {
    return "author: " + StringUtil.join(users, ", ")
  }

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || javaClass != o.javaClass) return false
    val filter = o as VcsLogUserFilterImpl
    return Comparing.haveEqualElements(users, filter.users)
  }

  override fun hashCode(): Int {
    return Comparing.unorderedHashcode(users)
  }

  companion object {
    private val LOG = Logger.getInstance(VcsLogUserFilterImpl::class.java)
  }
}