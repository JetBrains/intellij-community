// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.extensions

import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.extension
import kotlin.io.path.notExists

/**
 * Utility class to read the content of registrar files from Kotlin compiler plugins.
 */
object CompilerPluginRegistrarUtils {

    enum class RegistrarFile(val location: String) {
        /**
         * Default registrar location for FIR compiler plugins.
         */
        DEFAULT("META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar"),

        /**
         * Legacy registrar location.
         * Might be necessary to check to correctly detect old versions of compiler plugins' jars (see KT-52665).
         */
        LEGACY("META-INF/services/org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar"),
    }

    fun readRegistrarContent(compilerPluginJar: Path): String? =
        readFirstExistingFileContentFromJar(compilerPluginJar, RegistrarFile.entries.map { it.location })

    fun readRegistrarContent(compilerPluginJar: Path, registrarFile: RegistrarFile): String? =
        readFirstExistingFileContentFromJar(compilerPluginJar, listOf(registrarFile.location))
}

private fun readFirstExistingFileContentFromJar(jarFile: Path, pathsInJar: List<String>): String? {
    if (jarFile.notExists() || jarFile.extension != "jar") return null

    ZipFile(jarFile.toFile()).use { zipFile ->
        val entry = pathsInJar.firstNotNullOfOrNull { pathInJar -> zipFile.getEntry(pathInJar) } ?: return null

        return zipFile.getInputStream(entry).bufferedReader().use { it.readText() }
    }
}
