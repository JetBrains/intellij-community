// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.ui.layout.*
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.psi.KtBinaryExpression
import javax.swing.JComponent

class KotlinRangesHintsProvider : KotlinAbstractHintsProvider<KotlinRangesHintsProvider.Settings>() {

    object Settings

    override val name: String = KotlinBundle.message("hints.settings.ranges")

    override fun createSettings(): Settings = Settings

    override fun createConfigurable(settings: Settings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener): JComponent = panel {}

        override val cases: List<ImmediateConfigurable.Case>
            get() = listOf()

    }

    override fun isElementSupported(resolved: HintType?, settings: Settings): Boolean {
        return when (resolved) {
            HintType.RANGES -> true
            else -> false
        }
    }

    override val previewText: String = """
        val range = 0..10

        fun someFun() {    
            for (index in 0 until 10) {
                println(index)
            }
        }
    """.trimIndent()


}

internal fun KtBinaryExpression.isRangeExpression(): Boolean =
    with(operationReference.getReferencedNameAsName().asString()) {
        return this == ".." || this == "rangeTo" || this == "downTo" || this == "until"
    }