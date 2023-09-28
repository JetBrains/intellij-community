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

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.keymap.MacKeymapUtil
import java.awt.Font
import java.awt.GraphicsEnvironment

private val LOG = Logger.getInstance("presentationAssistant.MacKeyStrokePresentation")

val macKeyStrokesFont by lazy {
    val font = GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts.minByOrNull { getNonDisplayableMacSymbols(it).size }
    if (font != null) {
        val macSymbols = getNonDisplayableMacSymbols(font)
        if (macSymbols.isNotEmpty()) {
            LOG.warn("The following symbols from Mac shortcuts aren't supported in selected font: ${macSymbols.joinToString {it.first}}")
        }
    }
    font
}

private fun getNonDisplayableMacSymbols(font: Font) =
        MacKeymapUtil::class.java.declaredFields
                .filter { it.type == String::class.java && it.name != "APPLE" }
                .map { Pair(it.name, it.get(null) as String) }
                .filter { font.canDisplayUpTo(it.second) != -1 }
