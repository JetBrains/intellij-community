package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.utility
import com.intellij.driver.model.OnDispatcher

@Remote("com.intellij.notification.Notification")
interface Notification {
  fun getTitle(): String
  fun getContent(): String
  fun getGroupId(): String
  fun getActions(): List<AnAction>
}

@Remote("com.intellij.notification.ActionCenter")
interface ActionCenter {
  fun getNotifications(project: Project): List<Notification>
}

fun Driver.getNotifications(project: Project? = null): Collection<Notification> {
  return withContext(OnDispatcher.EDT) {
    utility<ActionCenter>().getNotifications(project ?: singleProject())
  }
}