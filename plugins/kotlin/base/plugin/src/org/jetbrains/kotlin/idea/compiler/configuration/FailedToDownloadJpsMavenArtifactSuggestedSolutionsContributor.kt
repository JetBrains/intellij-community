// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
interface FailedToDownloadJpsMavenArtifactSuggestedSolutionsContributor {
    companion object {
        private val EP_NAME: ExtensionPointName<FailedToDownloadJpsMavenArtifactSuggestedSolutionsContributor> =
            ExtensionPointName.create("org.jetbrains.kotlin.failedToDownloadJpsMavenArtifactSuggestedSolutionsContributor")

        fun getAllContributors(project: Project): List<FailedToDownloadJpsMavenArtifactSuggestedSolutionsContributor> =
            EP_NAME.getExtensionList(project)
    }

    @Nls
    fun getSuggestion(): String?
}
