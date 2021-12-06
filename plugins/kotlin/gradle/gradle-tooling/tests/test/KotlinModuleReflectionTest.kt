// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinModuleReflection
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

@Ignore
class KotlinModuleReflectionTest : AbstractKotlinKpmExtensionTest() {

    @Test
    fun `test name`() {
        assertEquals(kotlin.main.name, KotlinModuleReflection(kotlin.main).name)
        assertEquals(kotlin.test.name, KotlinModuleReflection(kotlin.test).name)
    }

    @Test
    fun `test fragments`() {
        kotlin.main.fragments.create("fragmentA")
        kotlin.main.fragments.create("fragmentB")

        assertEquals(
            kotlin.main.fragments.map { it.fragmentName },
            KotlinModuleReflection(kotlin.main).fragments?.map { it.fragmentName }
        )
    }

    @Test
    fun `test moduleClassifier`() {
        assertEquals(
            kotlin.main.moduleIdentifier.moduleClassifier,
            KotlinModuleReflection(kotlin.main).moduleClassifier
        )

        assertEquals(
            kotlin.test.moduleIdentifier.moduleClassifier,
            KotlinModuleReflection(kotlin.test).moduleClassifier
        )
    }
}
