// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinFragmentReflection
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Ignore
class KotlinFragmentReflectionTest : AbstractKotlinKpmExtensionTest() {

    @Test
    fun `test fragmentName`() {
        val fragment = kotlin.main.fragments.create("testFragment")
        val reflection = KotlinFragmentReflection(fragment)
        assertEquals("testFragment", reflection.fragmentName)
    }

    @Test
    fun `test directRefinesDependencies`() {
        val fragmentA = kotlin.main.fragments.create("a")
        val fragmentB = kotlin.main.fragments.create("b")
        fragmentB.refines(fragmentA)
        val reflectionB = KotlinFragmentReflection(fragmentB)

        assertEquals(
            fragmentB.directRefinesDependencies.map { it.fragmentName },
            reflectionB.directRefinesDependencies?.map { it.fragmentName }
        )
    }

    @Test
    fun `test containingModule`() {
        run {
            val fragment = kotlin.main.fragments.create("testFragment")
            assertEquals(
                kotlin.main.name, KotlinFragmentReflection(fragment).containingModule?.name
            )
        }

        run {
            val fragment = kotlin.test.fragments.create("testFragment")
            assertEquals(
                kotlin.test.name, KotlinFragmentReflection(fragment).containingModule?.name
            )
        }
    }

    @Test
    fun `test kotlinSourceRoots`() {
        val fragment = kotlin.main.fragments.create("testFragment")
        assertNotNull(KotlinFragmentReflection(fragment).kotlinSourceSourceRoots)
    }

    @Test
    fun `test languageSettings`() {
        val fragment = kotlin.main.fragments.create("testFragment")
        assertNotNull(KotlinFragmentReflection(fragment).languageSettings)
    }
}
