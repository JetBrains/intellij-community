// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.extensions

import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.notExists
import kotlin.io.path.readText

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

    FileSystems.newFileSystem(jarFile).use { fileSystem ->
        for (path in pathsInJar) {
            val resolvedPath = fileSystem.getPath(path)
            if (!resolvedPath.isRegularFile()) continue

            return resolvedPath.readText()
        }

        return null
    }
}
