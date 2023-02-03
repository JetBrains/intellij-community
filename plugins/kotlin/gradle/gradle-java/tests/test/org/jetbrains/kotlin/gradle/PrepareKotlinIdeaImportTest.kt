// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.gradle.newTests.testFeatures.GradleProjectsLinker
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradlePluginVersions
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.idea.gradleTooling.PrepareKotlinIdeImportTaskModel
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


class PrepareKotlinIdeaImportTest : MultiplePluginVersionGradleImportingTestCase() {

    private val KotlinToolingVersion.isPrepareKotlinIdeaImportSupportExpected: Boolean
        get() = this >= KotlinToolingVersion("1.6.255-SNAPSHOT")

    @Test
    @PluginTargetVersions(pluginVersion = "1.5+")
    fun testPrepareKotlinIdeaImport() {
        configureByFiles()

        val builtGradleModel = buildGradleModel<PrepareKotlinIdeImportTaskModel>()

        if (kotlinPluginVersion.isPrepareKotlinIdeaImportSupportExpected) {
            assertNull(builtGradleModel.getByProjectPathOrThrow(":p3"))

            /* Check root project */
            run {
                val rootModel = builtGradleModel.getNotNullByProjectPathOrThrow(":")

                assertEquals(
                    setOf("prepareKotlinIdeaImport"), rootModel.prepareKotlinIdeaImportTaskNames,
                    "Expected root module supporting the 'prepareKotlinIdeaImport' task"
                )

                assertEquals(
                    emptySet(), rootModel.legacyTaskNames,
                    "Expected no 'legacyTaskNames' on root module"
                )
            }

            /* Check p1 */
            run {
                val p1Model = builtGradleModel.getNotNullByProjectPathOrThrow(":p1")

                assertEquals(
                    setOf("prepareKotlinIdeaImport"), p1Model.prepareKotlinIdeaImportTaskNames,
                    "Expected 'p1' supporting the 'prepareKotlinIdeaImport' task"
                )

                assertEquals(
                    emptySet(), p1Model.legacyTaskNames,
                    "Expected no 'legacyTaskNames' on p1"
                )
            }

            /* Check p2 */
            run {
                val p2Model = builtGradleModel.getNotNullByProjectPathOrThrow(":p2")

                assertEquals(
                    emptySet(), p2Model.prepareKotlinIdeaImportTaskNames,
                    "Expected 'p2' not supporting the 'prepareKotlinIdeaImport' task"
                )

                assertEquals(
                    setOf("podImport"), p2Model.legacyTaskNames,
                    "Expected 'p2' to find the 'podImport' task name"
                )
            }
        } else {
            assertNull(
                builtGradleModel.getByProjectPathOrThrow(":"),
                "Expected no model for the root project"
            )

            assertNull(
                builtGradleModel.getByProjectPathOrThrow(":p3"),
                "Expected no model for empty p3 project"
            )

            /* Check p1 */
            run {
                val p1Model = builtGradleModel.getNotNullByProjectPathOrThrow(":p1")

                assertEquals(
                    emptySet(), p1Model.prepareKotlinIdeaImportTaskNames,
                    "Expected 'p1' to not support the 'prepareKotlinIdeaImport' task, yet"
                )

                assertEquals(
                    setOf("runCommonizer"), p1Model.legacyTaskNames,
                    "Expected 'p1' to detect the 'runCommonizer' task"
                )
            }

            /* Check p2 */
            run {
                val p2Model = builtGradleModel.getNotNullByProjectPathOrThrow(":p2")

                assertEquals(
                    emptySet<String>(), p2Model.prepareKotlinIdeaImportTaskNames,
                    "Expected 'p2' to not support the 'prepareKotlinIdeaImport' task"
                )

                assertEquals(
                    setOf("podImport"), p2Model.legacyTaskNames,
                    "Expected 'p2' to detect the 'podImport' task"
                )
            }
        }
    }

    @Test
    @PluginTargetVersions
    fun `testPrepareKotlinIdeaImport-compositeBuild`() {
        /* Only run against a single configuration */
        assumeTrue(kotlinPluginVersion == KotlinGradlePluginVersions.latest)
        configureByFiles()
        GradleProjectsLinker.linkGradleProject("consumerBuild", myProjectRoot.toNioPath().toFile(), myProject)
        val consumerStateFile = myProjectRoot.toNioPath().resolve("consumerBuild/consumerA/prepareKotlinIdeaImport.executed").toFile()
        if(!consumerStateFile.exists()) fail("consumerA: prepareKotlinIdeaImport not executed")
        if(consumerStateFile.readText() != "OK") fail("Unexpected content in consumerStateFile: ${consumerStateFile.readText()}")

        val producerStateFile = myProjectRoot.toNioPath().resolve("producerBuild/producerA/prepareKotlinIdeaImport.executed").toFile()
        if(!producerStateFile.exists()) fail("producerA: prepareKotlinIdeaImport not executed")
        if(producerStateFile.readText() != "OK") fail("Unexpected content in producerStateFile: ${consumerStateFile.readText()}")
    }
}
