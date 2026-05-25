// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.junit5

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.plugins.gradle.testFramework.util.createGradleWrapper
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest.Companion.configureByFiles
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinMppTestProperties
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinTestProperties
import org.jetbrains.kotlin.gradle.multiplatformTests.TestFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.CustomGradlePropertiesTestFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.GradleProjectsPublishingTestsFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.LinkedProjectPathsTestsFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.NoErrorEventsDuringImportFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.AllFilesAreUnderContentRootChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.DocumentationChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.LibraryKindsChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.ReferenceTargetChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.TestTasksChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.contentRoots.ContentRootsChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.facets.KotlinFacetSettingsChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.highlighting.HighlightingChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.hooks.KotlinMppTestHooks
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.orderEntries.OrderEntriesChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.runConfigurations.ExecuteRunConfigurationsChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.runConfigurations.RunConfigurationsChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.sources.LibrarySourcesChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.workspace.WorkspaceModelChecker
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.name

/**
 * Default feature set, mirroring [org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest.installedFeatures].
 *
 * Tests must opt into it explicitly via [KotlinTestFeaturesBuilder.useDefaultFeatures]; nothing is enabled out of the box.
 */
val kotlinDefaultTestFeatures: List<TestFeature<*>> = listOf(
    GradleProjectsPublishingTestsFeature,
    LinkedProjectPathsTestsFeature,
    NoErrorEventsDuringImportFeature,
    CustomGradlePropertiesTestFeature,

    ContentRootsChecker,
    KotlinFacetSettingsChecker,
    OrderEntriesChecker,
    TestTasksChecker,
    HighlightingChecker,
    RunConfigurationsChecker,
    ExecuteRunConfigurationsChecker,
    AllFilesAreUnderContentRootChecker,
    DocumentationChecker,
    ReferenceTargetChecker,
    KotlinMppTestHooks,
    LibraryKindsChecker,
    LibrarySourcesChecker,
)

/**
 * Test-facing handle for a configured bundle of [TestFeature]s.
 *
 * The enabled features list and the underlying [org.jetbrains.kotlin.gradle.multiplatformTests.TestConfiguration]
 * are intentionally kept private — tests should only need [check].
 */
interface KotlinTestFeatures {
    /**
     * Runs every enabled checker against [project] / [projectRoot], optionally tweaking configuration first
     * (analog of `doTest { testSpecificConfiguration }` in the JUnit4 base class).
     */
    fun check(
        project: Project,
        projectRoot: Path,
        block: KotlinTestFeaturesScope.() -> Unit = {},
    )
}

/**
 * Builder used inside `kotlinTestFeaturesFixture { ... }`.
 *
 * Manages the enabled feature set on top of the configuration scope: starts empty, `useDefaultFeatures()` pulls
 * in [kotlinDefaultTestFeatures], `add/exclude/only` tweak it.
 */
class KotlinTestFeaturesBuilder : KotlinTestFeaturesScope() {
    private val features = mutableListOf<TestFeature<*>>()

    fun useDefaultFeatures() {
        kotlinDefaultTestFeatures.forEach { if (it !in features) features += it }
    }

    fun add(vararg features: TestFeature<*>) {
        features.forEach { if (it !in this.features) this.features += it }
    }

    fun exclude(vararg features: TestFeature<*>) {
        val toRemove = features.toSet()
        this.features.removeAll { it in toRemove }
    }

    fun only(vararg features: TestFeature<*>) {
        this.features.clear()
        this.features += features
    }

    internal fun snapshotFeatures(): List<TestFeature<*>> = features.toList()
}

/**
 * Internal handle that also exposes the data needed by the rest of the JUnit5 pipeline
 * (project-root fixture, checker execution). Not part of the test-facing API.
 */
internal interface KotlinTestFeaturesInternal : KotlinTestFeatures {
    val enabledFeatures: List<TestFeature<*>>
    val scope: KotlinTestFeaturesScope
    val testProperties: KotlinTestProperties
    val testDataDir: Path
}

private class KotlinTestFeaturesImpl(
    override val enabledFeatures: List<TestFeature<*>>,
    override val scope: KotlinTestFeaturesScope,
    override val testProperties: KotlinTestProperties,
    override val testDataDir: Path,
) : KotlinTestFeaturesInternal {
    override fun check(
        project: Project,
        projectRoot: Path,
        block: KotlinTestFeaturesScope.() -> Unit,
    ) {
        scope.block()
        val testDataFile = testDataDir.toFile()
        val projectRootFile = projectRoot.toFile()
        enabledFeatures.forEach { feature ->
            // TODO: the feature set is going to be reworked. Generic TestFeature lifecycle hooks
            //       (beforeImport / afterImport with a KotlinSyncTestsContext) are not supported here yet.
            if (feature is WorkspaceModelChecker<*>) {
                feature.check(project, testDataFile, projectRootFile, testProperties, scope)
            }
        }
    }
}

/**
 * Configurable fixture bundling enabled checkers + their typed configuration, plus the per-versions
 * [KotlinTestProperties] used by every checker. Wire it into the rest of the test pipeline via
 * [projectRootFixture] / [KotlinTestFeatures.check].
 *
 * Example:
 * ```
 * val features = kotlinTestDataFixture.kotlinTestFeaturesFixture(versions) {
 *     only(ContentRootsChecker)
 *     configure(ContentRootsChecker) {
 *         hideResourceRoots = true
 *     }
 * }
 * ```
 */
fun TestFixture<KotlinTestData>.kotlinTestFeaturesFixture(
    versions: KmpVersions,
    block: KotlinTestFeaturesBuilder.() -> Unit = {},
): TestFixture<KotlinTestFeatures> = testFixture {
    val testData = this@kotlinTestFeaturesFixture.init()
    val builder = KotlinTestFeaturesBuilder().apply(block)
    val properties = KotlinMppTestProperties.constructRaw(versions.kotlin, versions.gradle, versions.agp)
    val features: KotlinTestFeatures = KotlinTestFeaturesImpl(
        enabledFeatures = builder.snapshotFeatures(),
        scope = builder,
        testProperties = properties,
        testDataDir = testData.testDataDir,
    )
    initialized(features) {}
}

/**
 * Project-root fixture wired to the [features] handle: copies testdata to a temp folder using the configuration
 * from the features bundle so that pre-import processors (e.g. KGP property substitution) see the same state
 * the checkers will later consume.
 */
fun TestFixture<KotlinTestFeatures>.projectRootFixture(
    versions: KmpVersions,
    testRootFixture: TestFixture<Path> = tempPathFixture(),
): TestFixture<Path> = testFixture {
    val features = this@projectRootFixture.init() as KotlinTestFeaturesInternal
    val testRoot = testRootFixture.init()
    val projectRoot = testRoot.resolve(features.testDataDir.name)
    projectRoot.createDirectory()
    projectRoot.createGradleWrapper(versions.gradle.version)

    writeAction {
        configureByFiles(
            projectRoot,
            features.testDataDir,
            features.scope,
            features.testProperties,
            features.enabledFeatures,
        )
    }
    initialized(projectRoot) {}
}
