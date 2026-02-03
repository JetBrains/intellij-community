// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.containers.MultiMap
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.util.VcsUserUtil
import org.jetbrains.annotations.ApiStatus

interface VcsLogUserResolver {
  fun resolveUserName(name: String): Set<VcsUser>
  fun resolveCurrentUser(root: VirtualFile): Set<VcsUser>
}

@ApiStatus.Internal
abstract class VcsLogUserResolverBase : VcsLogUserResolver {
  abstract val currentUsers: Map<VirtualFile, VcsUser>
  abstract val allUsers: Set<VcsUser>

  private val cachedUsers = SynchronizedClearableLazy {
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

    return@SynchronizedClearableLazy Pair(usersByNames, usersByEmails)
  }

  private val allUsersByNames: MultiMap<String, VcsUser> get() = cachedUsers.value.first
  private val allUsersByEmails: MultiMap<String, VcsUser> get() = cachedUsers.value.second

  protected fun buildCache() = cachedUsers.value
  protected fun clearCache() = cachedUsers.drop()

  override fun resolveUserName(name: String): Set<VcsUser> {
    val standardName = VcsUserUtil.getNameInStandardForm(name)
    return allUsersByNames[standardName].union(allUsersByEmails[standardName])
  }

  override fun resolveCurrentUser(root: VirtualFile): Set<VcsUser> {
    val vcsUser = currentUsers[root]
    if (vcsUser == null) return emptySet()

    val usersByName = resolveUserName(vcsUser.name)
    val emailNamePart = VcsUserUtil.getNameFromEmail(vcsUser.email) ?: return usersByName

    val emails = usersByName.map { user -> VcsUserUtil.emailToLowerCase(user.email) }.toSet()
    val usersByEmail = resolveUserName(emailNamePart).filter { candidateUser ->
      /*
      ivan@ivanov.com and ivan@petrov.com have the same emailNamePart, but they are different people
      resolveUserName("ivan") will find both of them, so we filter results here
      */
      emails.contains(VcsUserUtil.emailToLowerCase(candidateUser.email))
    }
    return usersByName + usersByEmail
  }
}