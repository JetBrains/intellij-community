// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification

import com.intellij.notification.impl.ui.NotificationsUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.LightPlatformTestCase

/**
 * @author Alexander Lobas
 */
class NotificationTest : LightPlatformTestCase() {
  fun testMessages() {
    assertEquals("Title (Subtitle): Content", buildStatusMessage("Title", "Subtitle", "Content"))

    assertEquals("Title: Hello world<<>><>", buildStatusMessage("Title", "Hello&nbsp;world&laquo;&raquo;&lt;&gt;"))

    assertEquals("Title: first line second line third // Action",
                 buildStatusMessage("Title",
                                    "<html><body> " +
                                    "<font size=\"3\">first line<br>" +
                                    "second line<br>" +
                                    "third<br>" +
                                    "<a href=\"create\">Action</a><br>" +
                                    "</body></html>"))

    assertEquals("Title: message", buildStatusMessage("Title", "<p>message</p>"))

    assertEquals("title: foo bar", buildStatusMessage("title", "foo<br/>bar"))

    assertEquals("title: foo bar", buildStatusMessage("title", "foo\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\nbar"))

    assertEquals("Title: Content // Action1 // Action2 // Action3", buildStatusMessage("Title", null, "Content", "Action1", "Action2", "Action3"))
  }

  private fun buildStatusMessage(title: String, content: String): String {
    return buildStatusMessage(title, null, content)
  }

  private fun buildStatusMessage(title: String, subtitle: String?, content: String, vararg actions: String): String {
    val notification = Notification("IDE-errors", title, content, NotificationType.ERROR)
    if (subtitle != null) {
      notification.subtitle = subtitle
    }
    for (action in actions) {
      notification.addAction(object : AnAction(action) {
        override fun actionPerformed(e: AnActionEvent) {
        }
      })
    }
    return NotificationsUtil.buildStatusMessage(notification)
  }
}