package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.RdTarget

@Remote("com.intellij.openapi.vcs.changes.ChangeListManager", rdTarget = RdTarget.BACKEND)
interface ChangeListManager {
  fun getChangeLists(): List<LocalChangeList>
  fun findChangeList(name: String): LocalChangeList?
  fun getDefaultChangeList(): LocalChangeList
  fun getChangeListsNumber(): Int
}

@Remote("com.intellij.openapi.vcs.changes.LocalChangeList")
interface LocalChangeList {
  fun getName(): String
  fun getComment(): String?
}

fun Driver.changeListManager(): ChangeListManager {
  return service<ChangeListManager>(singleProject())
}

fun Driver.listChangeListNames(): List<String> {
  return changeListManager().getChangeLists().map { it.getName() }
}

fun Driver.findChangeListByName(name: String): LocalChangeList? {
  return changeListManager().findChangeList(name)
}
