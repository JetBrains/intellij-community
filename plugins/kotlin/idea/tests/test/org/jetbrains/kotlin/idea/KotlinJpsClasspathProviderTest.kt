// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea

import com.intellij.testFramework.LightPlatformTestCase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit38ClassRunner::class)
class KotlinJpsClasspathProviderTest : LightPlatformTestCase() {
    private val KOTLIN_REFLECT_PREFIX = "kotlin-reflect"
    private val KOTLIN_COMPILER_COMMON_FOR_IDE_PREFIX = "kotlin-compiler-common-for-ide"
    private val KOTLIN_COMPILER_FE10_FOR_IDE_PREFIX = "kotlin-compiler-fe10-for-ide"
    private val KOTLIN_COMPILER_IR_FOR_IDE_PREFIX = "kotlin-compiler-ir-for-ide"

    fun testGetClassPath() {
        val inst = KotlinJpsClasspathProvider()
        val jars = inst.classPath

        assertEquals(4, jars.size)
        assertJar(jars[0], KOTLIN_REFLECT_PREFIX)
        assertJar(jars[1], KOTLIN_COMPILER_COMMON_FOR_IDE_PREFIX)
        assertJar(jars[2], KOTLIN_COMPILER_FE10_FOR_IDE_PREFIX)
        assertJar(jars[3], KOTLIN_COMPILER_IR_FOR_IDE_PREFIX)
    }

    private fun assertJar(jar: String?, prefix: String) {
        val jarName = File(jar!!).name
        assertTrue("Expected jar with prefix: $prefix, but $jarName found.",
                   jarName.startsWith(prefix))
    }
}