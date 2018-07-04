/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
@file:JvmName("ChangeListUtil")

package com.intellij.openapi.vcs.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.util.text.UniqueNameGenerator

private val CHANGELIST_NAME_PATTERN = "\\s\\[(.*)\\]"
private val STASH_MESSAGE_PATTERN = VcsBundle.message("stash.changes.message", ".*")
private val SYSTEM_CHANGELIST_REGEX = (STASH_MESSAGE_PATTERN + CHANGELIST_NAME_PATTERN).toRegex()

fun createSystemShelvedChangeListName(systemPrefix: String, changelistName: String): String {
  return "$systemPrefix [$changelistName]"
}

private fun getOriginalName(shelvedName: String): String {
  return SYSTEM_CHANGELIST_REGEX.matchEntire(shelvedName)?.groups?.get(1)?.value ?: shelvedName
}

fun getPredefinedChangeList(shelvedList: ShelvedChangeList, changeListManager: ChangeListManager): LocalChangeList? {
  val defaultName = shelvedList.DESCRIPTION
  return changeListManager.findChangeList(defaultName) ?:
         if (shelvedList.isMarkedToDelete) changeListManager.findChangeList(getOriginalName(defaultName)) else null
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
