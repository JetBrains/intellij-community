/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.actions

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.*

enum class ShortNameType(private val typeId: @NonNls String,
                         private val descriptionKey: @PropertyKey(resourceBundle = VcsBundle.BUNDLE) String) {
  INITIALS("initials", "annotations.short.name.type.initials"),
  LASTNAME("lastname", "annotations.short.name.type.last.name"),
  FIRSTNAME("firstname", "annotations.short.name.type.first.name"),
  NONE("full", "annotations.short.name.type.full.name"),
  EMAIL("email", "annotations.short.name.type.email");

  val description: @NlsActions.ActionText String get() = VcsBundle.message(descriptionKey)

  fun isSet(): Boolean {
    return typeId == PropertiesComponent.getInstance().getValue(KEY)
  }

  fun set() {
    PropertiesComponent.getInstance().setValue(KEY, typeId)
  }

  companion object {
    private const val KEY = "annotate.short.names.type" // NON-NLS

    private val DELIMITERS_REGEX = Regex("[.,<>()\":_-]")

    @JvmStatic
    fun shorten(input: String?, type: ShortNameType): String? {
      if (input == null) return null
      val rawName = StringUtil.collapseWhiteSpace(input)

      var name = rawName

      val emailStart = name.indexOf('<')
      val emailEnd = name.indexOf('>')
      val atSign = name.indexOf('@')
      if (0 <= emailStart && emailStart < atSign && atSign < emailEnd) {
        // "Vasya <vasya.pupkin@jetbrains.com> Pupkin" -> "vasya.pupkin@jetbrains.com"
        val email = name.substring(emailStart + 1, emailEnd).trim()
        if (type == EMAIL) return email

        // "Vasya <vasya.pupkin@jetbrains.com> Pupkin" -> "Vasya Pupkin"
        val prefix = name.substring(0, emailStart).trim()
        val suffix = name.substring(emailEnd + 1).trim()
        name = when {
          prefix.isNotEmpty() && suffix.isNotEmpty() -> "$prefix $suffix"
          prefix.isNotEmpty() -> prefix
          suffix.isNotEmpty() -> suffix
          else -> email
        }
      }

      if (type == NONE) {
        return name
      }

      if (type == EMAIL) {
        val atIndex = name.indexOf("@")
        if (atIndex == -1) {
          return name // email not found
        }

        // "Vasya vasya.pupkin@jetbrains.com Pupkin" -> "vasya.pupkin@jetbrains.com"
        var startIndex = name.lastIndexOf(" ", atIndex)
        if (startIndex == -1) startIndex = 0
        var endIndex = name.indexOf(" ", atIndex)
        if (endIndex == -1) endIndex = name.length
        return name.substring(startIndex, endIndex)
      }

      val atIndex = name.indexOf("@")
      if (atIndex > 0 && !name.contains(" ")) {
        // "vasya.pupkin@email.com" -> "vasya.pupkin"
        name = name.substring(0, atIndex)
      }

      name = name.replace(DELIMITERS_REGEX, " ")

      val strings = name.split(" ").filter { it.isNotBlank() }
      if (strings.isEmpty()) return rawName

      if (type == INITIALS) {
        return strings.joinToString(separator = "") { it[0].uppercase(Locale.getDefault()) }
      }

      val userName = when (type) {
        FIRSTNAME -> strings.first()
        LASTNAME -> strings.last()
        else -> throw IllegalArgumentException(type.name)
      }
      return userName.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault())
        else it.toString()
      }
    }
  }
}