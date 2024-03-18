// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import javax.swing.JComponent

@Deprecated("Use org.jetbrains.kotlin.idea.codeInsight.hints.declarative.KotlinLambdasHintsProvider instead")
class KotlinLambdasHintsProvider : KotlinAbstractHintsProvider<KotlinLambdasHintsProvider.Settings>() {

    data class Settings(
        var returnExpressions: Boolean = true,
        var implicitReceiversAndParams: Boolean = true,
    ): HintsSettings() {
        override fun isEnabled(hintType: HintType): Boolean =
            when(hintType) {
                HintType.LAMBDA_RETURN_EXPRESSION -> returnExpressions
                HintType.LAMBDA_IMPLICIT_PARAMETER_RECEIVER -> implicitReceiversAndParams
                else -> false
            }

        override fun enable(hintType: HintType, enable: Boolean) =
            when(hintType) {
                HintType.LAMBDA_RETURN_EXPRESSION -> returnExpressions = enable
                HintType.LAMBDA_IMPLICIT_PARAMETER_RECEIVER -> implicitReceiversAndParams = enable
                else -> Unit
            }
    }

    override val key: SettingsKey<Settings> = SettingsKey("kotlin.lambdas.hints")
    override val name: String = KotlinBundle.message("hints.settings.lambdas")

    override val group: InlayGroup
        get() = InlayGroup.LAMBDAS_GROUP

    override fun isElementSupported(resolved: HintType?, settings: Settings): Boolean =
        when (resolved) {
            HintType.LAMBDA_RETURN_EXPRESSION -> settings.returnExpressions
            HintType.LAMBDA_IMPLICIT_PARAMETER_RECEIVER -> settings.implicitReceiversAndParams
            else -> false
        }

    override fun isHintSupported(hintType: HintType): Boolean =
        hintType == HintType.LAMBDA_RETURN_EXPRESSION || hintType == HintType.LAMBDA_IMPLICIT_PARAMETER_RECEIVER

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

    override val previewText: String? = null
}