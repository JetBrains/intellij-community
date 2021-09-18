// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.codeInsight.hints.settings.InlaySettingsProvider
import com.intellij.ide.ui.OptionsSearchTopHitProvider
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.project.Project
import com.intellij.ui.layout.*
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinLanguage

class KotlinInlayHintsTopHitProvider : OptionsSearchTopHitProvider.ProjectLevelProvider {
    override fun getId(): String = "kotlin.inlay.hints"

    override fun getOptions(project: Project): MutableCollection<OptionDescription> =
        InlaySettingsProvider.EP.getExtensions().flatMap { it.createModels(project, KotlinLanguage.INSTANCE) }.flatMap {
            listOf(
                CheckboxDescriptor(
                    KotlinBundle.message(it.id),
                    PropertyBinding(
                        get = it::isEnabled,
                        set = { newValue ->
                            with(it) {
                                isEnabled = newValue
                                apply()
                            }
                        })
                ).asOptionDescriptor()
            ) +
                    it.cases.map { case ->
                        CheckboxDescriptor(
                            KotlinBundle.message("""${it.id}.${case.id}"""),
                            PropertyBinding(
                                get = case::value,
                                set = { newValue ->
                                    case.value = newValue
                                    if (newValue) {
                                        it.isEnabled = true
                                    }
                                    it.apply()
                                })
                        ).asOptionDescriptor()
                    }
        }.toMutableList()
}
