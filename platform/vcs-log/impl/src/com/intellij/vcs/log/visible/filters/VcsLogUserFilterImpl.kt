// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible.filters

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.ClearableLazyValue
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
        else -> {
          false
        }
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

interface VcsLogUserResolver {
  fun resolveUserName(name: String): Set<VcsUser>
  fun resolveCurrentUser(root: VirtualFile): Set<VcsUser>
}

abstract class VcsLogUserResolverBase : VcsLogUserResolver {
  abstract val currentUsers: Map<VirtualFile, VcsUser>
  abstract val allUsers: Set<VcsUser>

  private val cachedUsers = ClearableLazyValue.createAtomic {
    val usersByNames: MultiMap<String, VcsUser> = MultiMap.create()
    val usersByEmails: MultiMap<String, VcsUser> = MultiMap.create()

    for (user in allUsers) {
      val name = user.name
      if (name.isNotEmpty()) {
        usersByNames.putValue(VcsUserUtil.getNameInStandardForm(name), user)
      }
      val email = user.email
      val nameFromEmail = VcsUserUtil.getNameFromEmail(email)
      if (nameFromEmail != null) {
        usersByEmails.putValue(VcsUserUtil.getNameInStandardForm(nameFromEmail), user)
      }
    }

    return@createAtomic Pair(usersByNames, usersByEmails)
  }

  private val allUsersByNames: MultiMap<String, VcsUser> get() = cachedUsers.value.first
  private val allUsersByEmails: MultiMap<String, VcsUser> get() = cachedUsers.value.second

  protected fun buildCache() = cachedUsers.value

  override fun resolveUserName(name: String): Set<VcsUser> {
    val standardName = VcsUserUtil.getNameInStandardForm(name)
    return allUsersByNames[standardName].union(allUsersByEmails[standardName])
  }

  override fun resolveCurrentUser(root: VirtualFile): Set<VcsUser> {
    val vcsUser = currentUsers[root]
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

  companion object {
    private val LOG = logger<VcsLogUserResolverBase>()
  }
}

private class SimpleVcsLogUserResolver(override val currentUsers: Map<VirtualFile, VcsUser>,
                                       override val allUsers: Set<VcsUser>) : VcsLogUserResolverBase() {
  init {
    buildCache()
  }
}