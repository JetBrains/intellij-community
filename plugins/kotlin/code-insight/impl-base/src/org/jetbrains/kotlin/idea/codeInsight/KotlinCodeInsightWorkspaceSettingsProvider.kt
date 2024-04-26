// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.application.options.editor.AutoImportOptionsProvider
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.BeanConfigurable
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

class KotlinCodeInsightWorkspaceSettingsProvider(project: Project) : BeanConfigurable<KotlinCodeInsightWorkspaceSettings>(
    KotlinCodeInsightWorkspaceSettings.getInstance(project), KotlinBundle.message("code.insight.workspace.settings.title")
), AutoImportOptionsProvider {

    init {
        checkBox(
            ApplicationBundle.message("checkbox.add.unambiguous.imports.on.the.fly"),
            KotlinCodeInsightSettings.getInstance()::addUnambiguousImportsOnTheFly,
        )

        checkBox(ApplicationBundle.message("checkbox.optimize.imports.on.the.fly"), instance::optimizeImportsOnTheFly)
    }
}
