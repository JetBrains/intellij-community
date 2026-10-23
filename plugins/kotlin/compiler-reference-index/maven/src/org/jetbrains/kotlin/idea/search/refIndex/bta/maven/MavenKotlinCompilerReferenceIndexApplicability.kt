// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta.maven

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.search.refIndex.bta.BtaKotlinCompilerReferenceIndexApplicabilityProvider
import org.jetbrains.kotlin.idea.search.refIndex.bta.KOTLIN_CRI_GENERATION_PROPERTY
import org.jetbrains.kotlin.idea.search.refIndex.bta.isMavenCriGenerationEnabled

/**
 * Maven project property that enables Kotlin incremental compilation. Maven CRI generation requires it.
 */
private const val KOTLIN_INCREMENTAL_PROPERTY = "kotlin.compiler.incremental"

private const val KOTLIN_MAVEN_PLUGIN_ID = "kotlin-maven-plugin"

internal class MavenKotlinCompilerReferenceIndexApplicability : BtaKotlinCompilerReferenceIndexApplicabilityProvider {
    // This approximation ignores CI detection and property sources other than the POM properties
    override fun isApplicable(project: Project): Boolean = runReadActionBlocking {
        MavenProjectsManager.getInstanceIfCreated(project)?.projects?.any { mavenProject ->
            isMavenCriGenerationEnabled(
                mavenProject.properties.getProperty(KOTLIN_CRI_GENERATION_PROPERTY),
                mavenProject.properties.getProperty(KOTLIN_INCREMENTAL_PROPERTY),
                mavenProject.findPlugin(KOTLIN_MAVEN_GROUP_ID, KOTLIN_MAVEN_PLUGIN_ID)?.version?.let(IdeKotlinVersion::opt),
            )
        } ?: false
    }
}
