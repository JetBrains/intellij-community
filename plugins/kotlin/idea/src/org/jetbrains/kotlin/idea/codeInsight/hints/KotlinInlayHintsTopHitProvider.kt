// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.codeInsight.hints.settings.InlaySettingsProvider
import com.intellij.ide.ui.OptionsSearchTopHitProvider
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

class KotlinInlayHintsTopHitProvider : OptionsSearchTopHitProvider.ProjectLevelProvider {
    init {
        // the only reason is to suppress unused i18n property inspection
        // as it can not detect usage of combined key like `KotlinBundle.message("""${it.id}.${case.id}""")`
        listOf(
            KotlinBundle.message("parameter.hints.old"),
            KotlinBundle.message("kotlin.references.types.hints"),
            KotlinBundle.message("kotlin.references.types.hints.hints.type.function.parameter"),
            KotlinBundle.message("kotlin.references.types.hints.hints.type.function.return"),
            KotlinBundle.message("kotlin.references.types.hints.hints.type.variable"),
            KotlinBundle.message("kotlin.references.types.hints.hints.type.property"),
            KotlinBundle.message("kotlin.lambdas.hints"),
            KotlinBundle.message("kotlin.call.chains.hints"),
            KotlinBundle.message("kotlin.lambdas.hints.hints.lambda.receivers.parameters"),
            KotlinBundle.message("kotlin.lambdas.hints.hints.lambda.return"),
            KotlinBundle.message("kotlin.values.hints.kotlin.values.ranges"),
            KotlinBundle.message("microservices.url.path.inlay.hints"),
            KotlinBundle.message("vcs.code.author")
        )
    }

    override fun getId(): String = "kotlin.inlay.hints"

    override fun getOptions(project: Project): MutableCollection<OptionDescription> {
        val options = mutableListOf<OptionDescription>()

        InlaySettingsProvider.EP.getExtensions().flatMap { it.createModels(project, KotlinLanguage.INSTANCE) }.forEach {
            KotlinBundle.messageOrNull(it.id)?.let { msg ->
                options.add(
                    CheckboxDescriptor(
                        msg,
                        getter = it::isEnabled,
                        setter = { newValue ->
                            with(it) {
                                isEnabled = newValue
                                apply()
                                refreshHints(project)
                            }
                        }
                    ).asOptionDescriptor()
                )
            }

            it.cases.forEach { case ->
                // TODO: Have to clean up this hidden "gem" - maybe make `Case` open ?
                KotlinBundle.messageOrNull("${it.id}.${case.id}")?.let { msg ->
                    options.add(
                        CheckboxDescriptor(
                            msg,
                            getter = case::value,
                            setter = { newValue ->
                                case.value = newValue
                                if (newValue) {
                                    it.isEnabled = true
                                }
                                it.apply()
                                refreshHints(project)
                            }
                        ).asOptionDescriptor())
                }
            }
        }

        return options
    }
}
