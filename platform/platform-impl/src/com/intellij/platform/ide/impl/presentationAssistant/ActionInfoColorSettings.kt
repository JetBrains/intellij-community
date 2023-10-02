// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

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

