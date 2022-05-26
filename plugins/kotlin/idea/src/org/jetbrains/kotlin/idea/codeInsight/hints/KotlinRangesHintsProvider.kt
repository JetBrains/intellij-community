// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.*
import com.intellij.ui.layout.*
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.psi.KtBinaryExpression
import javax.swing.JComponent

class KotlinRangesHintsProvider : KotlinAbstractHintsProvider<NoSettings>() {

    object Settings

    override val key: SettingsKey<NoSettings> = SettingsKey("kotlin.ranges.hints")
    override val name: String = KotlinBundle.message("hints.settings.ranges")
    override val group: InlayGroup
        get() = InlayGroup.VALUES_GROUP

    override fun createSettings(): NoSettings = NoSettings()

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener): JComponent = panel {}

        override val cases: List<ImmediateConfigurable.Case>
            get() = listOf()

    }

    override fun isElementSupported(resolved: HintType?, settings: NoSettings): Boolean {
        return when (resolved) {
            HintType.RANGES -> true
            else -> false
        }
    }

    override val previewText: String = """
        fun someFun() {
            val range = 0..10
            for (i in 0..5) {}
            for (i in 5 until index) {}
            for (i in 5 downTo 0) {}
        }
    """.trimIndent()

    override val description: String
        get() = KotlinBundle.message("inlay.kotlin.ranges.hints")
}

internal fun KtBinaryExpression.isRangeExpression(): Boolean =
    with(operationReference.getReferencedNameAsName().asString()) {
        return this == ".." || this == "rangeTo" || this == "downTo" || this == "until"
    }