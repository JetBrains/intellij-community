// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import junit.framework.AssertionFailedError
import org.jetbrains.kotlin.gradle.newTests.OldMppTestsInfraDuplicate
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

@OldMppTestsInfraDuplicate
class AssociateCompilationsModelBuilderTest : MultiplePluginVersionGradleImportingTestCase() {

    @Test
    @PluginTargetVersions(pluginVersion = "1.6.20+")
    fun testAssociateCompilationIntegrationTest() {
        configureByFiles()
        val model = buildKotlinMPPGradleModel().getNotNullByProjectPathOrThrow(":p2")

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

private fun KotlinMPPGradleModel.getCompilation(targetName: String, compilationName: String): KotlinCompilation {
    return findCompilation(KotlinCompilationCoordinatesImpl(targetName, compilationName))
        ?: throw AssertionFailedError("Missing compilation: $targetName/$compilationName")
}
