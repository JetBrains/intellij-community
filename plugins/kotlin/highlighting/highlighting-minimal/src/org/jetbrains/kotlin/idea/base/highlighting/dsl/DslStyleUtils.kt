// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.highlighting.dsl

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.ide.highlighter.custom.CustomHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.NlsContexts.DetailedDescription
import com.intellij.openapi.util.ScalableIcon
import com.intellij.ui.LayeredIcon
import com.intellij.util.ui.ColorsIcon
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import javax.swing.Icon
import kotlin.math.absoluteValue

@ApiStatus.Internal
object DslStyleUtils {
    private const val STYLE_COUNT = 4

    private val STYLE_KEYS: List<TextAttributesKey> = listOf(
        CustomHighlighterColors.CUSTOM_KEYWORD1_ATTRIBUTES,
        CustomHighlighterColors.CUSTOM_KEYWORD2_ATTRIBUTES,
        CustomHighlighterColors.CUSTOM_KEYWORD3_ATTRIBUTES,
        CustomHighlighterColors.CUSTOM_KEYWORD4_ATTRIBUTES
    )

    private val styles: List<TextAttributesKey> = (1..STYLE_COUNT).map { index ->
        TextAttributesKey.createTextAttributesKey(externalKeyName(index), STYLE_KEYS[index - 1])
    }
    private val types: List<HighlightInfoType> = styles.map { attributeKey  ->
        HighlightInfoType.HighlightInfoTypeImpl(HighlightInfoType.SYMBOL_TYPE_SEVERITY, attributeKey, false)
    }

    val DSL_MARKER_CLASS_ID = ClassId.topLevel(FqName("kotlin.DslMarker"))

    val descriptionsToStyles: Map<String, TextAttributesKey> = (1..STYLE_COUNT).associate { index ->
        KotlinBundle.message("highlighter.name.dsl") + styleOptionDisplayName(index) to styleById(index)
    }

    private fun externalKeyName(index: Int) = "KOTLIN_DSL_STYLE$index"

    fun styleOptionDisplayName(index: Int): @DetailedDescription String =
        KotlinBundle.message("highlighter.name.style", index)

    fun styleIdByFQName(name: FqName): Int {
        return (name.asString().hashCode() % STYLE_COUNT).absoluteValue + 1
    }

    fun styleById(styleId: Int): TextAttributesKey = styles[styleId - 1]
    fun typeById(styleId: Int): HighlightInfoType = types[styleId - 1]

    fun createDslStyleIcon(styleId: Int): Icon {
        val globalScheme = EditorColorsManager.getInstance().globalScheme
        val markersColor = globalScheme.getAttributes(styleById(styleId)).foregroundColor
        val icon = LayeredIcon(2)
        val defaultIcon = KotlinIcons.DSL_MARKER_ANNOTATION
        icon.setIcon(defaultIcon, 0)
        icon.setIcon(
            (ColorsIcon(defaultIcon.iconHeight / 2, markersColor) as ScalableIcon).scale(JBUI.pixScale()),
            1,
            defaultIcon.iconHeight / 2,
            defaultIcon.iconWidth / 2
        )
        return icon
    }
}
