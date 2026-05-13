// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests.k2

import com.intellij.testFramework.TestDataPath
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.gradle.multiplatformTests.junit5.K2TestApplication
import org.jetbrains.kotlin.gradle.multiplatformTests.junit5.KmpParametrizedClass
import org.jetbrains.kotlin.gradle.multiplatformTests.junit5.KmpVersions
import org.jetbrains.kotlin.gradle.multiplatformTests.junit5.kotlinTestDataFixture
import org.jetbrains.kotlin.gradle.multiplatformTests.junit5.kotlinTestFeaturesFixture
import org.jetbrains.kotlin.gradle.multiplatformTests.junit5.projectRootFixture
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.contentRoots.ContentRootsChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.projectFixture
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.jupiter.api.Test

@TestDataPath($$"$PROJECT_ROOT/community/plugins/kotlin/idea/tests/testData/gradle")
@TestMetadata("kotlinGeneratedSourcesImportTest")
@K2TestApplication
@KmpParametrizedClass
class KotlinJvmGeneratedSourcesImportJUnit5Test(val versions: KmpVersions) {

    private val testDataFixture = kotlinTestDataFixture()

    private val featuresFixture = testDataFixture.kotlinTestFeaturesFixture(this.versions) {
        only(ContentRootsChecker)
    }
    private val features by featuresFixture

    private val projectRootFixture = featuresFixture.projectRootFixture(this.versions)
    private val projectRoot by projectRootFixture

    private val gradleFixture = gradleFixture(this.versions.gradle.version)
    private val gradle by gradleFixture

    private val project by gradleFixture.projectFixture(projectRootFixture)

    @Test
    @PluginTargetVersions(pluginVersion = "2.3.0+", gradleVersion = "7.6+")
    fun testGeneratedInMainSourceSet() = runBlocking {
        gradle.syncProject(project, projectRoot) {
            createDirectoriesForEmptyContentRoots()
        }
        features.check(project, projectRoot)
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.3.0+", gradleVersion = "7.6+")
    fun testGeneratedInTestSourceSet() = runBlocking {
        gradle.syncProject(project, projectRoot) {
            createDirectoriesForEmptyContentRoots()
        }
        features.check(project, projectRoot)
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.3.0+", gradleVersion = "7.6+")
    fun testGeneratedWithIdeaPlugin() = runBlocking {
        gradle.syncProject(project, projectRoot) {
            createDirectoriesForEmptyContentRoots()
        }
        features.check(project, projectRoot)
    }
}
