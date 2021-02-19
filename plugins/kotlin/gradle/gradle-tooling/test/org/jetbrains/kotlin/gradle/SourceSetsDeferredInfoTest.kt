/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle

import org.junit.Test
import kotlin.test.assertEquals

/* TODO NOW
class SourceSetsDeferredInfoTest {

    @Test
    fun testNoCoercionToCommon() {
        val sourceSets = makeSourceSets()
        computeSourceSetsDeferredInfo(sourceSets, allTargets, true, false)

        assertEquals(
            "Expected to have platforms mentioned in targets",
            setOf(KotlinPlatform.NATIVE, KotlinPlatform.JVM),
            sourceSets[commonSourceSet.name]?.actualPlatforms!!.platforms
        )
    }

    @Test
    fun testCoercionToCommon() {
        val sourceSets = makeSourceSets()
        computeSourceSetsDeferredInfo(sourceSets, allTargets, true, true)

        assertEquals(
            setOf(KotlinPlatform.COMMON),
            sourceSets[commonSourceSet.name]?.actualPlatforms!!.platforms,
            "Expected to have only common platform",
        )
    }

    companion object {
        private val MOCK_SETTINGS = KotlinLanguageSettingsImpl(
            null,
            null,
            false,
            emptySet(),
            emptySet(),
            emptyArray(),
            emptySet(),
            emptyArray()
        )

        private val mockOutput = KotlinCompilationOutputImpl(emptySet(), null, null)

        private val mockArguments = KotlinCompilationArgumentsImpl(emptyArray(), emptyArray())

        private val mockProperties = KotlinTaskPropertiesImpl(null, null, null, null)

        private val commonSourceSet = makeSourceSet("commonMain")
        private val jvmSourceSet = makeSourceSet("jvmMain", setOf(commonSourceSet.name))
        private val macosSourceSet = makeSourceSet("macosMain", setOf(commonSourceSet.name))

        private val jvmCompilation = makeCompilation("jvm", jvmSourceSet, KotlinPlatform.JVM)
        private val macosCompilation = makeCompilation("macos", macosSourceSet, KotlinPlatform.NATIVE)

        private val allTargets = listOf(
            makeTarget("jvm", KotlinPlatform.JVM, jvmCompilation),
            makeTarget("macos", KotlinPlatform.NATIVE, macosCompilation)
        )

        private fun makeSourceSet(name: String, dependsOn: Set<String> = emptySet()) = KotlinSourceSetImpl(
            name,
            MOCK_SETTINGS,
            emptySet(),
            emptySet(),
            emptyArray(),
            dependsOn
        )

        private fun makeSourceSets(): Map<String, KotlinSourceSetImpl> = listOf(commonSourceSet, macosSourceSet, jvmSourceSet).map {
            it.name to it
        }.toMap()

        private fun makeCompilation(name: String, sourceSet: KotlinSourceSetImpl, platform: KotlinPlatform) = KotlinCompilationImpl(
            name,
            setOf(sourceSet),
            setOf(sourceSet),
            emptyArray(),
            mockOutput,
            mockArguments,
            emptyArray(),
            mockProperties,
            null
        ).apply {
            this.platform = platform
        }

        private fun makeTarget(name: String, platform: KotlinPlatform, compilation: KotlinCompilationImpl) = KotlinTargetImpl(
            name,
            name,
            name,
            platform,
            listOf(compilation),
            emptyList(),
            emptyList(),
            null,
            emptyList()
        )
    }
}
*/
