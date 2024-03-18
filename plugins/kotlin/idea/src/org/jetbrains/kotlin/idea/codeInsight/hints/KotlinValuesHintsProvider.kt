// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import javax.swing.JComponent

@Deprecated("Use org.jetbrains.kotlin.idea.codeInsight.hints.declarative.KotlinValuesHintsProvider")
class KotlinValuesHintsProvider : KotlinAbstractHintsProvider<KotlinValuesHintsProvider.Settings>() {

    data class Settings(
        var ranges: Boolean = true
    ): HintsSettings() {
        override fun isEnabled(hintType: HintType): Boolean =
            when (hintType) {
                HintType.RANGES -> ranges
                else -> false
            }

        override fun enable(hintType: HintType, enable: Boolean) =
            when (hintType) {
                HintType.RANGES -> ranges = enable
                else -> Unit
            }
    }

    override val key: SettingsKey<Settings> = SettingsKey("kotlin.values.hints")
    override val name: String = KotlinBundle.message("hints.settings.values.ranges")
    override val group: InlayGroup
        get() = InlayGroup.VALUES_GROUP

    override fun createSettings(): Settings = Settings()

    override fun createConfigurable(settings: Settings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener): JComponent = panel {}

        override val mainCheckboxText: String = KotlinBundle.message("hints.settings.common.items")

        override val cases: List<ImmediateConfigurable.Case>
            get() = listOf(
                ImmediateConfigurable.Case(
                    KotlinBundle.message("hints.settings.values.ranges"),
                    "kotlin.values.ranges",
                    settings::ranges,
                    KotlinBundle.message("inlay.kotlin.values.hints.kotlin.values.ranges")
                )
            )

    }

    override fun isElementSupported(resolved: HintType?, settings: Settings): Boolean =
        when (resolved) {
            HintType.RANGES -> settings.ranges
            else -> false
        }

    override fun isHintSupported(hintType: HintType): Boolean = hintType == HintType.RANGES

    override val previewText: String? = null

    override val description: String
        get() = KotlinBundle.message("inlay.kotlin.values.hints")
}
