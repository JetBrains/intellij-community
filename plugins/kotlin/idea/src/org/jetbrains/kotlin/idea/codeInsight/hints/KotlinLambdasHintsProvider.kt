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
class KotlinLambdasHintsProvider : KotlinAbstractHintsProvider<KotlinLambdasHintsProvider.Settings>() {

    data class Settings(
        var returnExpressions: Boolean = true,
        var implicitReceiversAndParams: Boolean = true,
    )

    override val key: SettingsKey<Settings> = SettingsKey("kotlin.lambdas.hints")
    override val name: String = KotlinBundle.message("hints.settings.lambdas")
    override val hintsArePlacedAtTheEndOfLine = false

    override val group: InlayGroup
        get() = InlayGroup.LAMBDAS_GROUP

    override fun getProperty(key: String): String {
        return KotlinBundle.getMessage(key)
    }

    override fun isElementSupported(resolved: HintType?, settings: Settings): Boolean {
        return when (resolved) {
            HintType.LAMBDA_RETURN_EXPRESSION -> settings.returnExpressions
            HintType.LAMBDA_IMPLICIT_PARAMETER_RECEIVER -> settings.implicitReceiversAndParams
            else -> false
        }
    }

    override fun createConfigurable(settings: Settings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): JComponent = panel {}

            override val mainCheckboxText: String = KotlinBundle.message("hints.settings.common.items")

            override val cases: List<ImmediateConfigurable.Case>
                get() = listOf(
                    ImmediateConfigurable.Case(
                        KotlinBundle.message("hints.settings.lambda.return"),
                        "hints.lambda.return",
                        settings::returnExpressions,
                        KotlinBundle.message("inlay.kotlin.lambdas.hints.hints.lambda.return")
                    ),
                    ImmediateConfigurable.Case(
                        KotlinBundle.message("hints.settings.lambda.receivers.parameters"),
                        "hints.lambda.receivers.parameters",
                        settings::implicitReceiversAndParams,
                        KotlinBundle.message("inlay.kotlin.lambdas.hints.hints.lambda.receivers.parameters")
                    )
                )
        }
    }

    override val description: String
        get() = KotlinBundle.message("inlay.kotlin.lambdas.hints")

    override fun createSettings(): Settings = Settings()

    override val previewText: String = """
        val lambda = { i: Int ->
            i + 10
            i + 20
        }

        fun someFun() {    
            GlobalScope.launch {
                // someSuspendingFun()
            }
        }
    """.trimIndent()
}