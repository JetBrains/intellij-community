/*
 * Copyright 2000-2016 Nikolay Chashnikov.
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

/**
 * @author nik
 */
package org.nik.presentationAssistant

import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.PlatformUtils

enum class KeymapKind(val displayName: String, val defaultKeymapName: String) {
    WIN("Win/Linux", "\$default"),
    MAC("Mac", "Mac OS X 10.5+");

    fun getAlternativeKind() = when (this) {
        WIN -> MAC
        MAC -> if (PlatformUtils.isAppCode()) null else WIN
    }
}

fun getCurrentOSKind() = when {
    SystemInfo.isMac -> KeymapKind.MAC
    else -> KeymapKind.WIN
}

class KeymapDescription(var name: String = "", var displayText: String = "") {
    fun getKind() = if (name.contains("Mac OS")) KeymapKind.MAC else KeymapKind.WIN
    fun getKeymap() = KeymapManager.getInstance().getKeymap(name)

    override fun equals(other: Any?): Boolean {
        return other is KeymapDescription && other.name == name && other.displayText == displayText
    }

    override fun hashCode(): Int {
        return name.hashCode() + 31*displayText.hashCode()
    }
}

fun getDefaultMainKeymap() = KeymapDescription(getCurrentOSKind().defaultKeymapName, "")
fun getDefaultAlternativeKeymap() =
        getCurrentOSKind().getAlternativeKind()?.let { KeymapDescription(it.defaultKeymapName, "for ${it.displayName}") }
