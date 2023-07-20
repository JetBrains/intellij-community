/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.notification.impl

import com.intellij.notification.NotificationDisplayType
import com.intellij.openapi.util.text.Strings
import org.jdom.Element

/**
 * @author Konstantin Bulenkov
 */
data class NotificationSettings @JvmOverloads constructor(var groupId: String,
                                                          var displayType: NotificationDisplayType,
                                                          var isShouldLog: Boolean,
                                                          var isShouldReadAloud: Boolean,
                                                          var isPlaySound: Boolean = false) {

  fun withShouldLog(shouldLog: Boolean): NotificationSettings = copy(isShouldLog = shouldLog)

  fun withShouldReadAloud(shouldReadAloud: Boolean): NotificationSettings = copy(isShouldReadAloud = shouldReadAloud)

  fun withPlaySound(playSound: Boolean): NotificationSettings = copy(isPlaySound = playSound)

  fun withDisplayType(type: NotificationDisplayType): NotificationSettings = copy(displayType = type)

  fun save(): Element {
    return Element("notification").apply {
      setAttribute("groupId", groupId)
      if (displayType != NotificationDisplayType.BALLOON) setAttribute("displayType", displayType.toString())
      if (!isShouldLog) setAttribute("shouldLog", "false")
      if (isShouldReadAloud) setAttribute("shouldReadAloud", "true")
      if (isPlaySound) setAttribute("playSound", "true")
    }
  }

  companion object {
    fun load(element: Element): NotificationSettings? {
      val displayTypeString = element.getAttributeValue("displayType")
      var displayType = NotificationDisplayType.BALLOON
      var shouldLog = "false" != element.getAttributeValue("shouldLog")
      val shouldReadAloud = "true" == element.getAttributeValue("shouldReadAloud")
      val playSound = "true" == element.getAttributeValue("playSound")
      if ("BALLOON_ONLY" == displayTypeString) {
        shouldLog = false
        displayType = NotificationDisplayType.BALLOON
      }
      else if (displayTypeString != null) {
        try {
          displayType = NotificationDisplayType.valueOf(Strings.toUpperCase(displayTypeString))
        }
        catch (ignored: IllegalArgumentException) {
        }
      }
      val groupId = element.getAttributeValue("groupId")
      return groupId?.let { NotificationSettings(it, displayType, shouldLog, shouldReadAloud, playSound) }
    }
  }
}

internal fun isSoundEnabled(): Boolean = true