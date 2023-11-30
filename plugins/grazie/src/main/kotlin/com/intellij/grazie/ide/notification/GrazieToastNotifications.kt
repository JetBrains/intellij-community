// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.notification

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.remote.GrazieRemote
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.util.containers.MultiMap
import java.lang.ref.WeakReference

object GrazieToastNotifications {
  private enum class Group {
    LANGUAGES
  }

  private val shownNotifications = MultiMap.createConcurrent<Group, WeakReference<Notification>>()

  private val MISSING_LANGUAGES_GROUP
    get() = obtainGroup("Proofreading missing languages information")

  internal val GENERAL_GROUP
    get() = obtainGroup("Grazie notifications")

  fun showMissedLanguages(project: Project) {
    val langs = GrazieConfig.get().missedLanguages
    MISSING_LANGUAGES_GROUP
      .createNotification(msg("grazie.notification.missing-languages.title"),
                          msg("grazie.notification.missing-languages.body", langs.joinToString()),
                          NotificationType.WARNING)
      .addAction(object : NotificationAction(msg("grazie.notification.missing-languages.action.download")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          GrazieRemote.downloadMissing(project)
          notification.expire()
        }
      })
      .addAction(object : NotificationAction(msg("grazie.notification.missing-languages.action.disable")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          GrazieConfig.update { state ->
            state.copy(enabledLanguages = state.enabledLanguages - state.missedLanguages)
          }
          notification.expire()
        }
      })
      .setSuggestionType(true)
      .setDisplayId("grazie.missing.language")
      .expireAll(Group.LANGUAGES)
      .notify(project)
  }

  private fun Notification.expireAll(group: Group): Notification {
    whenExpired {
      shownNotifications.remove(group)?.forEach { it.get()?.expire() }
    }
    shownNotifications.putValue(group, WeakReference(this))
    return this
  }

  private fun obtainGroup(id: String): NotificationGroup {
    return NotificationGroupManager.getInstance().getNotificationGroup(id)
  }
}
