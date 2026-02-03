// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.projectStructure

import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.kotlin.idea.testFramework.gradle.KotlinGradleProjectTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsAtLeast
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsOlderThan
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatKotlinIsSupported
import org.junit.jupiter.params.ParameterizedTest
import kotlin.io.path.Path

@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
class KotlinGradleWorkspaceModelTest : KotlinGradleProjectTestCase() {

    private val currentSnapshot
        get() = gradleFixture.project.workspaceModel.currentSnapshot

    private val virtualFileUrlManager
        get() = gradleFixture.project.workspaceModel.getVirtualFileUrlManager()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `wsm should contain script definition entities after gradle sync`(gradleVersion: GradleVersion) {
        assumeThatGradleIsAtLeast(gradleVersion, "6.0") {
            "Script definitions were introduced in Gradle 6.0"
        }
        assumeThatGradleIsOlderThan(gradleVersion, "9.1.0") {
            "Script definitions classes were changed in Gradle 9.1.0"
        }
        test(gradleVersion, KOTLIN_PROJECT) {
            assertEqualsUnordered(
                listOf(
                    "org.gradle.kotlin.dsl.KotlinSettingsScript",
                    "org.gradle.kotlin.dsl.KotlinBuildScript",
                    "org.gradle.kotlin.dsl.KotlinInitScript"
                ), currentSnapshot.entities(GradleScriptDefinitionEntity::class.java).map { it.definitionId }.toList()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `wsm should contain script entities after gradle sync`(gradleVersion: GradleVersion) {
        assumeThatGradleIsAtLeast(gradleVersion, "6.0") {
            "Script definitions were introduced in Gradle 6.0"
        }
        test(gradleVersion, KOTLIN_PROJECT) {
            val projectPath = gradleFixture.project.basePath!!
            val expected = listOf("build.gradle.kts", "settings.gradle.kts").mapNotNull {
                    Path(projectPath).resolve(it)
                }.map { it.toVirtualFileUrl(virtualFileUrlManager) }

            assertEqualsUnordered(
                expected, currentSnapshot.entities(KotlinScriptEntity::class.java).map { it.virtualFileUrl }.toList()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `wsm should not contain script definition entities after gradle sync`(gradleVersion: GradleVersion) {
        assumeThatKotlinIsSupported(gradleVersion)
        assumeThatGradleIsOlderThan(gradleVersion, "6.0") {
            "Script definitions were introduced in Gradle 6.0"
        }
        test(gradleVersion, KOTLIN_PROJECT) {
            assert(currentSnapshot.entities(GradleScriptDefinitionEntity::class.java).none())
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `wsm should contain new script definition entities after gradle sync`(gradleVersion: GradleVersion) {
        assumeThatGradleIsAtLeast(gradleVersion, "9.1.0") {
            "Script definitions classes were changed in Gradle 9.1.0"
        }
        test(gradleVersion, KOTLIN_PROJECT) {
            assertEqualsUnordered(
                listOf(
                    "org.gradle.kotlin.dsl.KotlinGradleScriptTemplate",
                    "org.gradle.kotlin.dsl.KotlinProjectScriptTemplate",
                    "org.gradle.kotlin.dsl.KotlinSettingsScriptTemplate"
                ), currentSnapshot.entities(GradleScriptDefinitionEntity::class.java).map { it.definitionId }.toList()
            )
        }
    }
}
