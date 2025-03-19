package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import junit.framework.AssertionFailedError
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.GradleProjectsLinker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.buildGradleModel
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.buildKotlinMPPGradleModel
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.highlighting.HighlightingChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.hooks.KotlinMppTestHooks
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradlePluginVersions
import org.jetbrains.kotlin.idea.codeInsight.gradle.assertNoAndroidSourceSetInfo
import org.jetbrains.kotlin.idea.codeInsight.gradle.getAndroidSourceSetInfoOrFail
import org.jetbrains.kotlin.idea.codeInsight.gradle.getKotlinGradlePluginVersionOrFail
import org.jetbrains.kotlin.idea.codeInsight.gradle.getSourceSetOrFail
import org.jetbrains.kotlin.idea.gradleTooling.KotlinCompilationCoordinatesImpl
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradlePluginVersion
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.PrepareKotlinIdeImportTaskModel
import org.jetbrains.kotlin.idea.gradleTooling.compareTo
import org.jetbrains.kotlin.idea.gradleTooling.findCompilation
import org.jetbrains.kotlin.idea.gradleTooling.invokeWhenAtLeast
import org.jetbrains.kotlin.idea.gradleTooling.reparse
import org.jetbrains.kotlin.idea.gradleTooling.supportsKotlinAndroidMultiplatformSourceSetLayoutVersion2
import org.jetbrains.kotlin.idea.gradleTooling.supportsKotlinAndroidSourceSetInfo
import org.jetbrains.kotlin.idea.gradleTooling.toKotlinToolingVersion
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.Assume
import org.junit.Test

@TestMetadata("multiplatform/core/features/customImportTests")
class KotlinMppCustomImportingTests : AbstractKotlinMppGradleImportingTest() {
    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        // Disable all default checkers
        onlyCheckers(KotlinMppTestHooks)
        disableCheckers(HighlightingChecker)
        // Those tests don't run proper import, so source files will be mistreated as not under content root
        // We can't remove those sources because they are reused in other test runners (that actually check highlighting)
        allowFilesNotUnderContentRoot = true
    }

    @Test
    fun testKotlinGradlePluginVersionImporting() {
        doTest(runImport = false) {
            runAfterImport {
                val builtGradleModel = buildKotlinMPPGradleModel()
                val model = builtGradleModel.getNotNullByProjectPathOrThrow(":")

                assertEquals(kotlinPluginVersion, model.kotlinGradlePluginVersion?.toKotlinToolingVersion())
                val kotlinGradlePluginVersion = model.kotlinGradlePluginVersion!!

                /* Just check if those calls make it through classLoader boundaries */
                assertEquals(0, kotlinGradlePluginVersion.compareTo(kotlinPluginVersion))
                assertEquals(0, kotlinGradlePluginVersion.compareTo(kotlinPluginVersion.toString()))
                assertEquals(0, kotlinGradlePluginVersion.compareTo(KotlinGradlePluginVersion.parse(kotlinGradlePluginVersion.versionString)!!))

                assertNotNull(kotlinGradlePluginVersion.invokeWhenAtLeast(kotlinPluginVersion) { })
                assertNotNull(kotlinGradlePluginVersion.invokeWhenAtLeast(kotlinPluginVersion.toString()) {})
                assertNotNull(
                    kotlinGradlePluginVersion.invokeWhenAtLeast(KotlinGradlePluginVersion.parse(kotlinGradlePluginVersion.versionString)!!) {}
                )

                assertNotSame(kotlinGradlePluginVersion, kotlinGradlePluginVersion.reparse()!!)
                assertEquals(kotlinGradlePluginVersion.reparse(), kotlinGradlePluginVersion.reparse()!!)
            }
        }
    }

    @Test
    fun testPrepareKotlinIdeaImport() = doTest(runImport = false) {
        runBeforeImport {
            val builtGradleModel = buildGradleModel(PrepareKotlinIdeImportTaskModel::class)

            assertNull(builtGradleModel.getByProjectPathOrThrow(":p3"))

            /* Check root project */
            run {
                val rootModel = builtGradleModel.getNotNullByProjectPathOrThrow(":")

                kotlin.test.assertEquals(
                    setOf("prepareKotlinIdeaImport"), rootModel.prepareKotlinIdeaImportTaskNames,
                    "Expected root module supporting the 'prepareKotlinIdeaImport' task"
                )

                kotlin.test.assertEquals(
                    emptySet(), rootModel.legacyTaskNames,
                    "Expected no 'legacyTaskNames' on root module"
                )
            }

            /* Check p1 */
            run {
                val p1Model = builtGradleModel.getNotNullByProjectPathOrThrow(":p1")

                kotlin.test.assertEquals(
                    setOf("prepareKotlinIdeaImport"), p1Model.prepareKotlinIdeaImportTaskNames,
                    "Expected 'p1' supporting the 'prepareKotlinIdeaImport' task"
                )

                kotlin.test.assertEquals(
                    emptySet(), p1Model.legacyTaskNames,
                    "Expected no 'legacyTaskNames' on p1"
                )
            }

            /* Check p2 */
            run {
                val p2Model = builtGradleModel.getNotNullByProjectPathOrThrow(":p2")

                kotlin.test.assertEquals(
                    emptySet(), p2Model.prepareKotlinIdeaImportTaskNames,
                    "Expected 'p2' not supporting the 'prepareKotlinIdeaImport' task"
                )

                kotlin.test.assertEquals(
                    setOf("podImport"), p2Model.legacyTaskNames,
                    "Expected 'p2' to find the 'podImport' task name"
                )
            }
        }
    }

    @Test
    fun `testPrepareKotlinIdeaImport-compositeBuild`() = doTest(runImport = false) {
        runBeforeImport {
            /* Only run against a single configuration */
            Assume.assumeTrue(kotlinPluginVersion == KotlinGradlePluginVersions.latest)

            GradleProjectsLinker.linkGradleProject("consumerBuild", myProjectRoot.toNioPath().toFile(), myProject)
            val consumerStateFile = myProjectRoot.toNioPath().resolve("consumerBuild/consumerA/prepareKotlinIdeaImport.executed").toFile()
            if (!consumerStateFile.exists()) fail("consumerA: prepareKotlinIdeaImport not executed")
            if (consumerStateFile.readText() != "OK") fail("Unexpected content in consumerStateFile: ${consumerStateFile.readText()}")

            val producerStateFile = myProjectRoot.toNioPath().resolve("producerBuild/producerA/prepareKotlinIdeaImport.executed").toFile()
            if (!producerStateFile.exists()) fail("producerA: prepareKotlinIdeaImport not executed")
            if (producerStateFile.readText() != "OK") fail("Unexpected content in producerStateFile: ${consumerStateFile.readText()}")
        }
    }

    @Test
    fun testImportKotlinAndroidSourceSetInfo() = doTest(runImport = false) {
        runBeforeImport {
            val model = buildKotlinMPPGradleModel().getNotNullByProjectPathOrThrow(":")

            val commonMain = model.getSourceSetOrFail("commonMain")
            val commonTest = model.getSourceSetOrFail("commonTest")
            val jvmMain = model.getSourceSetOrFail("jvmMain")
            val jvmTest = model.getSourceSetOrFail("jvmTest")

            commonMain.assertNoAndroidSourceSetInfo()
            commonTest.assertNoAndroidSourceSetInfo()
            jvmMain.assertNoAndroidSourceSetInfo()
            jvmTest.assertNoAndroidSourceSetInfo()

            if (model.getKotlinGradlePluginVersionOrFail().supportsKotlinAndroidMultiplatformSourceSetLayoutVersion2()) {
                val androidMainInfo = model.getSourceSetOrFail("androidMain").getAndroidSourceSetInfoOrFail()
                assertEquals("androidMain", androidMainInfo.kotlinSourceSetName)
                assertEquals("main", androidMainInfo.androidSourceSetName)
                assertEquals(setOf("debug", "release"), androidMainInfo.androidVariantNames)

                val androidDebugInfo = model.getSourceSetOrFail("androidDebug").getAndroidSourceSetInfoOrFail()
                assertEquals("androidDebug", androidDebugInfo.kotlinSourceSetName)
                assertEquals("debug", androidDebugInfo.androidSourceSetName)
                assertEquals(setOf("debug"), androidDebugInfo.androidVariantNames)

                val androidUnitTestInfo = model.getSourceSetOrFail("androidUnitTest").getAndroidSourceSetInfoOrFail()
                assertEquals("androidUnitTest", androidUnitTestInfo.kotlinSourceSetName)
                assertEquals("test", androidUnitTestInfo.androidSourceSetName)
                assertEquals(setOf("debugUnitTest", "releaseUnitTest"), androidUnitTestInfo.androidVariantNames)

                val androidInstrumentedTestInfo = model.getSourceSetOrFail("androidInstrumentedTest").getAndroidSourceSetInfoOrFail()
                assertEquals("androidInstrumentedTest", androidInstrumentedTestInfo.kotlinSourceSetName)
                assertEquals("androidTest", androidInstrumentedTestInfo.androidSourceSetName)
                assertEquals(setOf("debugAndroidTest"), androidInstrumentedTestInfo.androidVariantNames)
            } else {
                assertFalse(model.getKotlinGradlePluginVersionOrFail().supportsKotlinAndroidSourceSetInfo())
                model.getSourceSetOrFail("androidMain").assertNoAndroidSourceSetInfo()
                model.getSourceSetOrFail("androidTest").assertNoAndroidSourceSetInfo()
                model.getSourceSetOrFail("androidAndroidTest").assertNoAndroidSourceSetInfo()
            }
        }
    }

    @Test
    @TestMetadata("../misc/associateCompilationIntegrationTest")
    fun testAssociateCompilationIntegrationTest() = doTest(runImport = false) {
        runBeforeImport {
            val model = buildKotlinMPPGradleModel().getNotNullByProjectPathOrThrow(":kmm")

            /* Test all associate coordinates can be resolved */
            model.targets.flatMap { it.compilations }.flatMap { it.associateCompilations }.forEach { coordinates ->
                assertNotNull(
                    "Missing compilation for coordinates: $coordinates",
                    model.findCompilation(coordinates)
                )
            }


            /* Test 'main' compilations do not have any associate compilations */
            model.targets.map { it.compilations.single { it.name == "main" } }.forEach { compilation ->
                assertTrue(
                    "Expected no associateCompilations for ${compilation.platform}/${compilation.name}",
                    compilation.associateCompilations.isEmpty()
                )
            }

            /* Check jvm/test */
            run {
                val jvmTest = model.getCompilation("jvm", "test")
                assertEquals(setOf(KotlinCompilationCoordinatesImpl("jvm", "main")), jvmTest.associateCompilations)
            }

            /* Check linuxX64/test */
            run {
                val linuxX64Test = model.getCompilation("linuxX64", "test")
                assertEquals(setOf(KotlinCompilationCoordinatesImpl("linuxX64", "main")), linuxX64Test.associateCompilations)
            }

            /* Check linuxArm64/test */
            run {
                val linuxX64Test = model.getCompilation("linuxArm64", "test")
                assertEquals(setOf(KotlinCompilationCoordinatesImpl("linuxArm64", "main")), linuxX64Test.associateCompilations)
            }
        }
    }
}

private fun KotlinMPPGradleModel.getCompilation(targetName: String, compilationName: String): KotlinCompilation {
    return findCompilation(KotlinCompilationCoordinatesImpl(targetName, compilationName))
        ?: throw AssertionFailedError("Missing compilation: $targetName/$compilationName")
}
