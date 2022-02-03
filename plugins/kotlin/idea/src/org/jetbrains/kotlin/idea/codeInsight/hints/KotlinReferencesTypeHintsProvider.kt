// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.ui.layout.*
import org.jetbrains.kotlin.idea.KotlinBundle
import javax.swing.JComponent

@Suppress("UnstableApiUsage")
class KotlinReferencesTypeHintsProvider : KotlinAbstractHintsProvider<KotlinReferencesTypeHintsProvider.Settings>() {

    data class Settings(
        var propertyType: Boolean = false,
        var localVariableType: Boolean = false,
        var functionReturnType: Boolean = false,
        var parameterType: Boolean = false
    )

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

    override val previewText: String = """
        val property = listOf(1, 2, 3).filter { num -> num % 2 == 0 }
        
        fun someFun(arg: Int) = print(arg)
        
        fun anotherFun(a: Int = 10, b: Int = 5): Int {
            val variable = a + b
            return variable * 2
        }

        fun yetAnotherFun() {
            Stream.of(1, 2, 3)
                .map { i -> i + 12 }
                .filter { i -> i % 2 == 0 }
                .collect(Collectors.toList())
        }
    """.trimIndent()

    override fun getProperty(key: String): String {
        return KotlinBundle.getMessage(key)
    }
}