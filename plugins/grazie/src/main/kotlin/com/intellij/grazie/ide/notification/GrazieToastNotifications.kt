// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.notification

import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.utils.englishName
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.detection.toLanguage
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.remote.GrazieRemote
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.Nls
import java.lang.ref.WeakReference
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

private val logger = logger<GrazieToastNotifications>()

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
    val config = GrazieConfig.get()
    val langs = config.missedLanguages.map { it.toLanguage() }.toSet()
    val folder = GrazieDynamic.dynamicFolder
    logger.info("Languages are stored in: $folder, entries of the folder: ${folder.listDirectoryEntries().joinToString { it.name }}")
    logger.info("Missing languages: ${langs.map { it.iso }}, Enabled languages: ${config.enabledLanguages.map { it.iso }}")
    MISSING_LANGUAGES_GROUP
      .createNotification(msg("grazie.notification.missing-languages.title"),
                          msg("grazie.notification.missing-languages.body", langs.joinToString { it.englishName }),
                          NotificationType.WARNING)
      .addAction(object : NotificationAction(msg("grazie.notification.missing-languages.action.download", langs)) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          GrazieRemote.downloadMissing(project)
          notification.expire()
          logger.info("Missing languages have been downloaded")
        }
      })
      .addAction(object : NotificationAction(msg("grazie.notification.missing-languages.action.disable", langs)) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          GrazieConfig.update { state ->
            state.copy(enabledLanguages = state.enabledLanguages - state.missedLanguages)
          }
          notification.expire()
          logger.info("Missing languages have been disabled")
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

  @Nls
  private fun msg(nlsPropertyPrefix: String, langs: Set<Language>): String {
    return when {
      langs.size == 1 -> msg("$nlsPropertyPrefix.singular", langs.first().englishName)
      else -> msg("$nlsPropertyPrefix.plural")
    }
  }
}
