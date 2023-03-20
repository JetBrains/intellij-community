// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ChangeListUtil")

package com.intellij.openapi.vcs.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.text.UniqueNameGenerator
import org.jetbrains.annotations.Nls

private const val CHANGELIST_NAME_PATTERN = "\\s\\[(.*)\\]"  // NON-NLS
private val STASH_MESSAGE_PATTERN get() = VcsBundle.message("stash.changes.message", ".*")
private val SYSTEM_CHANGELIST_REGEX get() = (STASH_MESSAGE_PATTERN + CHANGELIST_NAME_PATTERN).toRegex()

fun createSystemShelvedChangeListName(systemPrefix: @Nls(capitalization = Nls.Capitalization.Sentence) String,
                                      changelistName: @NlsSafe String): @Nls(capitalization = Nls.Capitalization.Sentence) String {
  return "$systemPrefix [$changelistName]"
}

private fun getOriginalName(shelvedName: String): String {
  return SYSTEM_CHANGELIST_REGEX.matchEntire(shelvedName)?.groups?.get(1)?.value ?: shelvedName
}

fun getPredefinedChangeList(shelvedList: ShelvedChangeList, changeListManager: ChangeListManager): LocalChangeList? {
  val defaultName = shelvedList.DESCRIPTION
  return changeListManager.findChangeList(defaultName)
         ?: if (shelvedList.isMarkedToDelete) changeListManager.findChangeList(getOriginalName(defaultName)) else null
}

fun getChangeListNameForUnshelve(shelvedList: ShelvedChangeList): String {
  val defaultName = shelvedList.DESCRIPTION
  return if (shelvedList.isMarkedToDelete) getOriginalName(defaultName) else defaultName
}

fun createNameForChangeList(project: Project, commitMessage: String): String {
  val changeListManager = ChangeListManager.getInstance(project)
  val proposedName = commitMessage.trim()
    .substringBefore('\n')
    .trim()
    .replace("[ ]{2,}".toRegex(), " ")
  return UniqueNameGenerator.generateUniqueName(proposedName, "", "", "-", "", { changeListManager.findChangeList(it) == null })
}

fun onChangeListAvailabilityChanged(projectConnection: MessageBusConnection, callback: Runnable) {
  projectConnection.subscribe(ChangeListListener.TOPIC, object : ChangeListListener {
    override fun changeListAvailabilityChanged() {
      callback.run()
    }
  })
}
