// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization

import junit.framework.TestCase
import org.jetbrains.kotlin.utils.PathUtil
import org.jetbrains.kotlinx.serialization.compiler.diagnostic.VersionReader
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

class RuntimeLibraryInClasspathTest {
    private val runtimeLibraryPath = getSerializationCoreLibraryJar()

    @Test
    fun testRuntimeLibraryExists() {
        TestCase.assertNotNull(
            "kotlinx-serialization runtime library is not found. Make sure it is present in test classpath",
            runtimeLibraryPath
        )
    }

    @Test
    fun testRuntimeHasSufficientVersion() {
        val version = VersionReader.getVersionsFromManifest(runtimeLibraryPath!!)
        assertTrue(version.currentCompilerMatchRequired(), "Runtime version too high")
        assertTrue(version.implementationVersionMatchSupported(), "Runtime version too low")
    }
}

internal fun getSerializationCoreLibraryJar(): File? = try {
    PathUtil.getResourcePathForClass(Class.forName("kotlinx.serialization.KSerializer"))
} catch (e: ClassNotFoundException) {
    null
}

internal fun getSerializationJsonLibraryJar(): File? = try {
    PathUtil.getResourcePathForClass(Class.forName("kotlinx.serialization.json.Json"))
} catch (e: ClassNotFoundException) {
    null
}
