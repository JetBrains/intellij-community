// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.shared.completion

import org.jetbrains.idea.completion.api.*

private class KotlinGradleDependencyCompletionContributor : DependencyCompletionContributor {
    override fun isApplicable(context: DependencyCompletionContext): Boolean {
        return context == GradleDependencyCompletionContext
    }

    override suspend fun search(request: DependencyCompletionRequest): List<DependencyCompletionResult> {
        //TODO: implement
        val searchString = request.searchString
        // com.android.tools.idea.gradle.completion.GradleDependencyCompletionContributorTest.testBasicCompletionInGradleKtsFile_qualifiedClosure
        if (searchString.startsWith("androidx")) return emptyList()
        val items = searchString.split(":")
        val g = items.getOrNull(0) ?: "mygroup"
        val a = items.getOrNull(1) ?: "myartifact"
        val v = items.getOrNull(2) ?: "myversion"
        return listOf(
            DependencyCompletionResult(g, a, v + "1"),
            DependencyCompletionResult(g, a, v + "2"),
            DependencyCompletionResult(g, "$a-impl", v),
            DependencyCompletionResult("$g-lib", "$a-impl", v),
        )
    }

    override suspend fun getGroups(request: DependencyGroupCompletionRequest): List<String> {
        //TODO: implement
        return listOf(request.groupPrefix + "-commons", request.groupPrefix + "-framework")
    }

    override suspend fun getArtifacts(request: DependencyArtifactCompletionRequest): List<String> {
        //TODO: implement
        return listOf(request.artifactPrefix + "-api", request.artifactPrefix + "-impl")
    }

    override suspend fun getVersions(request: DependencyVersionCompletionRequest): List<String> {
        //TODO: implement
        return listOf("1.0.0", "2.0.0")
    }
}
