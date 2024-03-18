// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.test

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.TestVcsNotifier
import com.intellij.testFramework.UsefulTestCase.assertOrderedEquals
import org.junit.Assert.assertEquals

fun assertHasNotification(type: NotificationType,
                          title: String,
                          content: String,
                          actions: List<String>?,
                          notifications: Collection<Notification>): Notification {
  val cleanupForAssertionContent = cleanupForAssertion(content)
  val notification = notifications.find {
    type == it.type && title == it.title && cleanupForAssertionContent == cleanupForAssertion(it.content)
  } ?: notifications.lastOrNull()

  try {
    if (notification == null) {
      throw AssertionError("No $type notification '${title}|${content}' was shown" +
                           notifications.joinToString("\n") { it.title + "|" + it.content }
                             .run { if (isNotEmpty()) "\n\n" + this else this })
    }
    assertEquals("Incorrect notification type: " + tos(notification), type, notification.type)
    assertEquals("Incorrect notification title: " + tos(notification), title, notification.title)
    assertEquals("Incorrect notification content: " + tos(notification),
                 cleanupForAssertionContent, cleanupForAssertion(notification.content))
    if (actions != null) {
      assertOrderedEquals("Incorrect notification actions", notification.actions.map { it.templatePresentation.text }, actions)
    }
  }
  catch (e: Throwable) {
    notifications.print()
    throw e
  }

  return notification
}

fun assertHasNotification(type: NotificationType, title: String, content: String, notifications: Collection<Notification>): Notification {
  return assertHasNotification(type, title, content, null, notifications)
}

fun assertNotification(type: NotificationType, title: String, content: String, actions: List<String>?, actual: Notification): Notification {
  assertHasNotification(type, title, content, actions, setOf(actual))
  return actual
}

fun assertNotification(type: NotificationType, title: String, content: String, actual: Notification) =
  assertNotification(type, title, content, null, actual)

fun assertSuccessfulNotification(title: String, content: String, actual: Notification) =
  assertNotification(NotificationType.INFORMATION, title, content, actual)

fun assertSuccessfulNotification(title: String, content: String, actions: List<String>, actual: Notification) =
  assertNotification(NotificationType.INFORMATION, title, content, actions, actual)

fun cleanupForAssertion(content: String): String {
  val nobr = content.replace("<br/>", "\n").replace("<br>", "\n").replace("<hr/>", "\n")
    .replace("&nbsp;", " ").replace(" {2,}".toRegex(), " ")
  return nobr.lines()
    .map { line -> line.replace(" href='[^']*'".toRegex(), "").trim({ it <= ' ' }) }
    .filter { line -> !line.isEmpty() }
    .joinToString(" ")
}

private fun tos(notification: Notification): String {
  return "${notification.title}|${notification.content}"
}

private val LOG = logger<TestVcsNotifier>()
private fun Collection<Notification>.print() {
  LOG.warn("Available notifications: " + joinToString(separator = "\n", transform = Notification::toString))
}
