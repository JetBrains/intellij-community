// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.compiler.configuration.FailedToDownloadJpsMavenArtifactSuggestedSolutionsContributor
import org.jetbrains.plugins.gradle.settings.GradleSettings

internal class GradleFailedToDownloadSuggestedSolutionsContributor(val project: Project) :
    FailedToDownloadJpsMavenArtifactSuggestedSolutionsContributor {

    @Nls
    override fun getSuggestion(): String? {
        val isAnyModuleCompiledByJps = GradleSettings.getInstance(project).linkedProjectsSettings.any { !it.delegatedBuild }
        return if (isAnyModuleCompiledByJps) KotlinIdeaGradleBundle.message("you.can.delegate.to.gradle") else null
    }
}
