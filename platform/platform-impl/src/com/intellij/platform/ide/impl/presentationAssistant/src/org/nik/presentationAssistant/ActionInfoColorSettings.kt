/*
 * Copyright 2000-2017 Nikolay Chashnikov.
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

package org.nik.presentationAssistant

import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.psi.codeStyle.DisplayPriority
import com.intellij.psi.codeStyle.DisplayPrioritySortable
import com.intellij.ui.JBColor
import java.awt.Color

/**
 * @author nik
 */
val FOREGROUND_COLOR_KEY = ColorKey.createColorKey("PRESENTATION_ASSISTANT_HINT_FOREGROUND", JBColor.black)
val BACKGROUND_COLOR_KEY = ColorKey.createColorKey("PRESENTATION_ASSISTANT_HINT_BACKGROUND", JBColor(Color(186, 238, 186, 120), Color(73, 117, 73)))

class ActionInfoColorSettings : ColorSettingsPage, DisplayPrioritySortable {
    override fun getDisplayName() = "Presentation Assistant"
    override fun getColorDescriptors(): Array<ColorDescriptor> {
        return arrayOf(
                ColorDescriptor("Hint Foreground", FOREGROUND_COLOR_KEY, ColorDescriptor.Kind.FOREGROUND),
                ColorDescriptor("Hint Background", BACKGROUND_COLOR_KEY, ColorDescriptor.Kind.BACKGROUND)
        )
    }

    override fun getPriority() = DisplayPriority.COMMON_SETTINGS

    override fun getIcon() = null
    override fun getDemoText() = " "
    override fun getHighlighter() = PlainSyntaxHighlighter()
    override fun getAdditionalHighlightingTagToDescriptorMap() = null
    override fun getAttributeDescriptors() = emptyArray<AttributesDescriptor>()
}

