/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.test

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.testFramework.UsefulTestCase.assertOrderedEquals
import org.junit.Assert.assertEquals
import org.picocontainer.MutablePicoContainer

inline fun <reified Int : Any, reified Impl : Int> overrideService(project: Project): Impl {
  val key = Int::class.java.name
  val picoContainer = project.picoContainer as MutablePicoContainer
  picoContainer.unregisterComponent(key)
  picoContainer.registerComponentImplementation(key, Impl::class.java)
  return project.service<Int>() as Impl
}

inline fun <reified Int : Any, reified Impl : Int> overrideService(): Impl {
  val key = Int::class.java.name
  val picoContainer = ApplicationManager.getApplication().picoContainer as MutablePicoContainer
  picoContainer.unregisterComponent(key)
  picoContainer.registerComponentImplementation(key, Impl::class.java)
  return service<Int>() as Impl
}

fun assertNotification(type: NotificationType, title: String, content: String, actions: List<String>?, actual: Notification): Notification {
  assertEquals("Incorrect notification type: " + tos(actual), type, actual.type)
  assertEquals("Incorrect notification title: " + tos(actual), title, actual.title)
  assertEquals("Incorrect notification content: " + tos(actual), cleanupForAssertion(content), cleanupForAssertion(actual.content))
  if (actions != null) assertOrderedEquals("Incorrect notification actions", actual.actions.map { it.templatePresentation.text }, actions)
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
