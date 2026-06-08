// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.projectStructure

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.testFramework.gradle.KotlinGradleProjectTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.jetbrains.plugins.gradle.testFramework.util.KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS
import org.jetbrains.plugins.gradle.testFramework.util.KOTLIN_SUPPORTED_VERSIONS
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass


@GradleProjectTestApplication
@ParameterizedClass
@AllGradleVersionsSource
@TargetVersions(KOTLIN_SUPPORTED_VERSIONS, KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
class KotlinGradleWorkspaceModelTest(private val _gradleVersion: GradleVersion) : KotlinGradleProjectTestCase() {

    @Test
    fun `test script definition entities after gradle sync`() {
        test(_gradleVersion, KOTLIN_PROJECT) {
            // Script definitions classes were changed in Gradle 9.1.0
            val expectedDefinitionIds = when (GradleVersionUtil.isGradleAtLeast(gradleVersion, "9.1.0")) {
                true -> listOf(
                    "org.gradle.kotlin.dsl.KotlinGradleScriptTemplate",
                    "org.gradle.kotlin.dsl.KotlinProjectScriptTemplate",
                    "org.gradle.kotlin.dsl.KotlinSettingsScriptTemplate"
                )
                else -> listOf(
                    "org.gradle.kotlin.dsl.KotlinSettingsScript",
                    "org.gradle.kotlin.dsl.KotlinBuildScript",
                    "org.gradle.kotlin.dsl.KotlinInitScript"
                )
            }
            val actualDefinitionIds = project.workspaceModel.currentSnapshot.entities(GradleScriptDefinitionEntity::class.java)
                .map { it.definitionId }
                .toList()
            assertEqualsUnordered(expectedDefinitionIds, actualDefinitionIds)
        }
    }

    @Test
    fun `wsm should contain script entities after gradle sync`() {
        test(_gradleVersion, KOTLIN_PROJECT) {
            val expectedEntities = sequenceOf("build.gradle.kts", "settings.gradle.kts")
                .mapNotNull { projectNioPath.resolve(it) }
                .map { it.toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager()) }
                .toList()
            val actualEntities = project.workspaceModel.currentSnapshot.entities(KotlinScriptEntity::class.java)
                .map { it.virtualFileUrl }
                .toList()
            assertEqualsUnordered(expectedEntities, actualEntities)
        }
    }
}
