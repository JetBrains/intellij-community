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

/**
 * @author Konstantin Bulenkov
 */
enum class ShortNameType(private val myId: @NonNls String,
                         private val myDescriptionKey: @PropertyKey(resourceBundle = VcsBundle.BUNDLE) String) {
  INITIALS("initials", "annotations.short.name.type.initials"),
  LASTNAME("lastname", "annotations.short.name.type.last.name"),
  FIRSTNAME("firstname", "annotations.short.name.type.first.name"),
  NONE("full", "annotations.short.name.type.full.name");

  val description: @NlsActions.ActionText String get() = VcsBundle.message(myDescriptionKey)

  fun isSet(): Boolean {
    return myId == PropertiesComponent.getInstance().getValue(KEY)
  }

  fun set() {
    PropertiesComponent.getInstance().setValue(KEY, myId)
  }

  companion object {
    private const val KEY = "annotate.short.names.type" // NON-NLS

    @JvmStatic
    fun shorten(name: String?, type: ShortNameType): String? {
      var name = name ?: return null
      if (type == NONE) return name

      val atOffset = name.indexOf('@')
      val emailStart = name.indexOf('<')
      val emailEnd = name.indexOf('>')
      if (0 < emailStart && emailStart < atOffset && atOffset < emailEnd) {
        // Vasya Pupkin <vasya.pupkin@jetbrains.com> -> Vasya Pupkin
        name = name.substring(0, emailStart).trim()
      }
      else if (!name.contains(" ") && atOffset > 0) {
        // vasya.pupkin@email.com --> vasya.pupkin
        name = name.substring(0, atOffset)
      }

      name = name.replace('.', ' ').replace('_', ' ').replace('-', ' ')

      val strings = StringUtil.split(name, " ")
      if (type == INITIALS) {
        return StringUtil.join(strings, { StringUtil.toUpperCase(it[0]).toString() }, "")
      }

      if (strings.size < 2) return name

      val shortName = when (type) {
        FIRSTNAME -> strings.first()
        else -> strings.last()
      }
      return StringUtil.capitalize(shortName)
    }
  }
}