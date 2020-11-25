package org.jetbrains.kotlin.gradle

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalKotlinMPPGradleModelExtensionsApi::class)
class KotlinMppGradleModelExtensionsTest {

    @Test
    fun getDeclaredDependsOnSourceSets() {
        val commonMain = createKotlinSourceSet("commonMain")
        val appleMain = createKotlinSourceSet("appleMain", dependsOnSourceSets = setOf("commonMain"))
        val macosMain = createKotlinSourceSet("macosMain", dependsOnSourceSets = setOf("appleMain"))
        val iosMain = createKotlinSourceSet("iosMain", dependsOnSourceSets = setOf("appleMain", "commonMain"))

        val model = createKotlinMPPGradleModel(
            sourceSets = setOf(commonMain, appleMain, macosMain, iosMain)
        )

        assertEquals(
            emptySet(), model.getDeclaredDependsOnSourceSets(commonMain),
            "Expected no declared dependency source sets for commonMain"
        )

        assertEquals(
            setOf(commonMain), model.getDeclaredDependsOnSourceSets(appleMain),
            "Expected only declared dependency for 'appleMain'"
        )

        assertEquals(
            setOf(appleMain, commonMain), model.getDeclaredDependsOnSourceSets(iosMain),
            "Expected only declared dependency for 'iosMain'"
        )
    }

    @Test
    fun `getAllDependsOnSourceSets is graceful for missing source sets`() {
        val commonMain = createKotlinSourceSet("commonMain")
        val macosMain = createKotlinSourceSet("macosMain", dependsOnSourceSets = setOf("commonMain", "missing"))
        val model = createKotlinMPPGradleModel(sourceSets = setOf(commonMain, macosMain))

        assertEquals(
            setOf(commonMain), model.getDeclaredDependsOnSourceSets(macosMain),
            "Expected declaredDependencySourceSets to ignore missing dependency source set"
        )
    }

    @Test
    fun getAllDependsOnSourceSets() {
        val commonMain = createKotlinSourceSet("commonMain")
        val appleMain = createKotlinSourceSet("appleMain", dependsOnSourceSets = setOf("commonMain"))
        val x64Main = createKotlinSourceSet("x64Main", dependsOnSourceSets = setOf("commonMain"))
        val macosMain = createKotlinSourceSet("macosMain", dependsOnSourceSets = setOf("appleMain"))
        val macosX64Main = createKotlinSourceSet("macosX64Main", dependsOnSourceSets = setOf("appleMain", "x64Main"))
        val macosArm64Main = createKotlinSourceSet("macosArm64Main", dependsOnSourceSets = setOf("appleMain"))

        val model = createKotlinMPPGradleModel(sourceSets = setOf(commonMain, appleMain, x64Main, macosMain, macosX64Main, macosArm64Main))

        assertEquals(
            setOf(appleMain, x64Main, commonMain),
            model.getAllDependsOnSourceSets(macosX64Main),
        )

        assertEquals(
            setOf(appleMain, x64Main, commonMain).sortedBy { it.name },
            model.getAllDependsOnSourceSets(macosX64Main).sortedBy { it.name }.toList(),
        )

        assertEquals(
            setOf(appleMain, commonMain),
            model.getAllDependsOnSourceSets(macosArm64Main).toSet(),
        )

        assertEquals(
            setOf(commonMain),
            model.getAllDependsOnSourceSets(appleMain).toSet(),
            "Expected only 'commonMain' for 'appleMain'"
        )

        assertEquals(
            emptySet(), model.getAllDependsOnSourceSets(commonMain).toSet(),
            "Expected empty set for 'commonMain'"
        )
    }

    @Test
    fun isDependsOn() {
        val commonMain = createKotlinSourceSet("commonMain")
        val appleMain = createKotlinSourceSet("appleMain", dependsOnSourceSets = setOf("commonMain"))
        val x64Main = createKotlinSourceSet("x64Main", dependsOnSourceSets = setOf("commonMain"))
        val macosX64Main = createKotlinSourceSet("macosX64Main", dependsOnSourceSets = setOf("appleMain", "x64Main"))
        val macosArm64Main = createKotlinSourceSet("macosArm64Main", dependsOnSourceSets = setOf("appleMain"))


        val model = createKotlinMPPGradleModel(sourceSets = setOf(commonMain, appleMain, x64Main, macosX64Main))

        assertTrue(
            model.isDependsOn(from = appleMain, to = commonMain),
            "Expected isDependsOn from appleMain to commonMain"
        )

        assertTrue(
            model.isDependsOn(from = x64Main, to = commonMain),
            "Expected isDependsOn from x64Main to commonMain"
        )

        assertTrue(
            model.isDependsOn(from = macosX64Main, to = commonMain),
            "Expected isDependsOn from macosX64Main to commonMain"
        )

        assertTrue(
            model.isDependsOn(from = macosX64Main, to = appleMain),
            "Expected isDependsOn from macosX64Main to appleMain"
        )

        assertTrue(
            model.isDependsOn(from = macosX64Main, to = commonMain),
            "Expected isDependsOn from macosX64Main to commonMain"
        )

        assertTrue(
            model.isDependsOn(from = macosX64Main, to = appleMain),
            "Expected isDependsOn from macosX64Main to appleMain"
        )

        assertTrue(
            model.isDependsOn(from = macosX64Main, to = x64Main),
            "Expected isDependsOn from macosX64Main to x64Main"
        )

        assertTrue(
            model.isDependsOn(from = macosArm64Main, to = commonMain),
            "Expected isDependsOn from macosArm64Main to commonMain"
        )

        assertTrue(
            model.isDependsOn(from = macosArm64Main, to = appleMain),
            "Expected isDependsOn from macosArm64Main to appleMain"
        )

        assertFalse(
            model.isDependsOn(from = macosArm64Main, to = x64Main),
            "Expected false isDependsOn from macosArm64 to x64Main"
        )

        for (sourceSet in model.sourceSets.values) {
            assertFalse(
                model.isDependsOn(from = commonMain, to = sourceSet),
                "Expected false isDependsOn from commonMain to ${sourceSet.name}"
            )

            assertFalse(
                model.isDependsOn(from = sourceSet, to = sourceSet),
                "Expected false isDependsOn for same source set"
            )

            assertFalse(
                sourceSet.isDependsOn(model, sourceSet),
                "Expected false isDependsOn for same source set"
            )
        }
    }

    @Test
    fun compilationDependsOnSourceSet() {
        val commonMain = createKotlinSourceSet("commonMain")
        val appleMain = createKotlinSourceSet("appleMain", dependsOnSourceSets = setOf("commonMain"))
        val macosMain = createKotlinSourceSet("macosMain", dependsOnSourceSets = setOf("appleMain"))
        val iosMain = createKotlinSourceSet("iosMain", dependsOnSourceSets = setOf("appleMain"))

        val metadataCompilation = createKotlinCompilation(sourceSets = setOf(commonMain))
        val macosMainCompilation = createKotlinCompilation(sourceSets = setOf(macosMain))
        val iosMainCompilation = createKotlinCompilation(sourceSets = setOf(iosMain))

        val model = createKotlinMPPGradleModel(sourceSets = setOf(commonMain, appleMain, macosMain, iosMain))

        assertTrue(
            model.compilationDependsOnSourceSet(iosMainCompilation, iosMain),
            "Expected iosMainCompilation depending on iosMain"
        )

        assertTrue(
            model.compilationDependsOnSourceSet(iosMainCompilation, appleMain),
            "Expected iosMainCompilation depending on appleMain"
        )

        assertTrue(
            model.compilationDependsOnSourceSet(iosMainCompilation, commonMain),
            "Expected iosMainCompilation depending on commonMain"
        )

        assertFalse(
            model.compilationDependsOnSourceSet(iosMainCompilation, macosMain),
            "Expected iosMainCompilation *not* depending on macosMain"
        )

        assertTrue(
            model.compilationDependsOnSourceSet(macosMainCompilation, macosMain),
            "Expected macosMainCompilation depending on macosMain"
        )

        assertTrue(
            model.compilationDependsOnSourceSet(macosMainCompilation, appleMain),
            "Expected macosMainCompilation depending on appleMain"
        )

        assertTrue(
            model.compilationDependsOnSourceSet(macosMainCompilation, commonMain),
            "Expected macosMainCompilation depending on commonMain"
        )

        assertFalse(
            model.compilationDependsOnSourceSet(macosMainCompilation, iosMain),
            "Expected macosMainCompilation *not* depending on iosMain"
        )

        assertTrue(
            model.compilationDependsOnSourceSet(metadataCompilation, commonMain),
            "Expected metadataCompilation depending on commonMain"
        )

        assertFalse(
            model.compilationDependsOnSourceSet(metadataCompilation, appleMain),
            "Expected metadataCompilation *not* depending on appleMain"
        )

        assertFalse(
            model.compilationDependsOnSourceSet(metadataCompilation, macosMain),
            "Expected metadataCompilation *not* depending on macosMain"
        )

        assertFalse(
            model.compilationDependsOnSourceSet(metadataCompilation, iosMain),
            "Expected metadataCompilation *not* depending on iosMain"
        )
    }

    @Test
    fun getCompilations() {
        val commonMain = createKotlinSourceSet("commonMain")
        val appleMain = createKotlinSourceSet("appleMain", dependsOnSourceSets = setOf("commonMain"))
        val macosMain = createKotlinSourceSet("macosMain", dependsOnSourceSets = setOf("appleMain"))

        val metadataCompilation = createKotlinCompilation(sourceSets = setOf(commonMain))
        val macosMainCompilation = createKotlinCompilation(sourceSets = setOf(macosMain))

        val metadataTarget = createKotlinTarget("metadata", compilations = setOf(metadataCompilation))
        val macosTarget = createKotlinTarget("macos", compilations = setOf(macosMainCompilation))

        val model = createKotlinMPPGradleModel(
            sourceSets = setOf(commonMain, appleMain, macosMain),
            targets = setOf(metadataTarget, macosTarget)
        )

        assertEquals(
            setOf(metadataCompilation, macosMainCompilation),
            model.getCompilations(commonMain),
            "Expected correct compilations for commonMain"
        )

        assertEquals(
            setOf(macosMainCompilation), model.getCompilations(appleMain),
            "Expected correct compilations for appleMain"
        )

        assertEquals(
            setOf(macosMainCompilation), model.getCompilations(macosMain),
            "Expected correct compilations for macosMain"
        )
    }
}
