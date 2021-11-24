// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea

import com.intellij.testFramework.LightPlatformTestCase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit38ClassRunner::class)
class KotlinJpsClasspathProviderTest : LightPlatformTestCase() {

    fun testGetClassPath() {
        val inst = KotlinJpsClasspathProvider()

        val jars = inst.classPath

        assertEquals(2, jars.size)
        assertTrue(jars[0], File(jars[0]!!).name.startsWith("kotlin-reflect"))
        assertTrue(jars[1], File(jars[1]!!).name.startsWith("kotlin-compiler-for-ide") || File(jars[1]!!).name == "kotlin-plugin.jar")
    }
}