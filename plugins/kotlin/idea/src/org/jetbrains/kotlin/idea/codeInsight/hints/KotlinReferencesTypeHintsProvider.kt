// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import javax.swing.JComponent

@Deprecated("Use org.jetbrains.kotlin.idea.codeInsight.hints.declarative.KotlinReferencesTypeHintsProvider instead")
class KotlinReferencesTypeHintsProvider : KotlinAbstractHintsProvider<KotlinReferencesTypeHintsProvider.Settings>() {

    override val hintsPriority: Int = 0

    data class Settings(
        var propertyType: Boolean = false,
        var localVariableType: Boolean = false,
        var functionReturnType: Boolean = false,
        var parameterType: Boolean = false
    ): HintsSettings() {
        override fun isEnabled(hintType: HintType) =
            when(hintType) {
                HintType.PROPERTY_HINT -> propertyType
                HintType.LOCAL_VARIABLE_HINT -> localVariableType
                HintType.FUNCTION_HINT -> functionReturnType
                HintType.PARAMETER_TYPE_HINT -> parameterType
                else -> false
            }

        override fun enable(hintType: HintType, enable: Boolean) =
            when(hintType) {
                HintType.PROPERTY_HINT -> propertyType = enable
                HintType.LOCAL_VARIABLE_HINT -> localVariableType = enable
                HintType.FUNCTION_HINT -> functionReturnType = enable
                HintType.PARAMETER_TYPE_HINT -> parameterType = enable
                else -> Unit
            }
    }

    override val key: SettingsKey<Settings> = SettingsKey("kotlin.references.types.hints")
    override val name: String = KotlinBundle.message("hints.settings.types")
    override val group: InlayGroup
        get() = InlayGroup.TYPES_GROUP

    override fun createConfigurable(settings: Settings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
                override fun createComponent(listener: ChangeListener): JComponent = panel {}

                override val mainCheckboxText: String = KotlinBundle.message("hints.settings.common.items")

                override val cases: List<ImmediateConfigurable.Case>
                    get() = listOf(
                        ImmediateConfigurable.Case(
                            KotlinBundle.message("hints.settings.types.property"),
                            "hints.type.property",
                            settings::propertyType,
                            KotlinBundle.message("inlay.kotlin.references.types.hints.hints.type.property")
                        ),
                        ImmediateConfigurable.Case(
                            KotlinBundle.message("hints.settings.types.variable"),
                            "hints.type.variable",
                            settings::localVariableType,
                            KotlinBundle.message("inlay.kotlin.references.types.hints.hints.type.variable")
                        ),
                        ImmediateConfigurable.Case(
                            KotlinBundle.message("hints.settings.types.return"),
                            "hints.type.function.return",
                            settings::functionReturnType,
                            KotlinBundle.message("inlay.kotlin.references.types.hints.hints.type.function.return")
                        ),
                        ImmediateConfigurable.Case(
                            KotlinBundle.message("hints.settings.types.parameter"),
                            "hints.type.function.parameter",
                            settings::parameterType,
                            KotlinBundle.message("inlay.kotlin.references.types.hints.hints.type.function.parameter")
                        ),
                    )
            }
    }

    override val description: String
        get() = KotlinBundle.message("inlay.kotlin.references.types.hints")

    override fun createSettings(): Settings = Settings()

    override fun isElementSupported(resolved: HintType?, settings: Settings): Boolean {
        return when (resolved) {
            HintType.PROPERTY_HINT -> settings.propertyType
            HintType.LOCAL_VARIABLE_HINT -> settings.localVariableType
            HintType.FUNCTION_HINT -> settings.functionReturnType
            HintType.PARAMETER_TYPE_HINT -> settings.parameterType
            else -> false
        }
    }

    override fun isHintSupported(hintType: HintType): Boolean =
        hintType == HintType.PROPERTY_HINT || hintType == HintType.LOCAL_VARIABLE_HINT ||
                hintType == HintType.FUNCTION_HINT || hintType == HintType.PARAMETER_TYPE_HINT

    override val previewText: String? = null
}