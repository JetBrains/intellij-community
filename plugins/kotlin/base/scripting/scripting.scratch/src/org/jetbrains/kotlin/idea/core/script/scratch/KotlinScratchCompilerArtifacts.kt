// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.scratch

import com.intellij.util.io.Decompressor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactNames

const val kotlincIdeScratchBuildTxtFileName: String = "build.txt"
const val kotlincIdeScratchDirectoryName: String = "kotlinc.ide.scratch"

private const val KOTLINC_IDE_SCRATCH_STAMP_FILE_NAME = ".source-stamp"

val kotlincIdeScratchHomeArtifactFileNames: List<String> = listOf(
    KotlinArtifactNames.KOTLIN_PRELOADER,
    KotlinArtifactNames.KOTLIN_COMPILER,
    KotlinArtifactNames.KOTLIN_STDLIB,
    KotlinArtifactNames.KOTLIN_REFLECT,
    KotlinArtifactNames.KOTLIN_SCRIPT_RUNTIME,
    KotlinArtifactNames.KOTLINX_COROUTINES_CORE_JVM,
    KotlinArtifactNames.KOTLIN_DAEMON,
    KotlinArtifactNames.POWER_ASSERT_COMPILER_PLUGIN,
    KotlinArtifactNames.KOTLIN_SCRIPTING_COMPILER,
    KotlinArtifactNames.KOTLIN_SCRIPTING_COMPILER_IMPL,
    KotlinArtifactNames.KOTLIN_SCRIPTING_COMMON,
    KotlinArtifactNames.KOTLIN_SCRIPTING_JVM,
    KotlinArtifactNames.JETBRAINS_ANNOTATIONS,
)
val kotlincIdeScratchClasspathArtifactFileNames: List<String> =
    kotlincIdeScratchHomeArtifactFileNames.filterNot { it == KotlinArtifactNames.KOTLIN_PRELOADER }

val kotlincIdeScratchHomeRelativePaths: List<String> =
    listOf(kotlincIdeScratchBuildTxtFileName) + kotlincIdeScratchHomeArtifactFileNames.map { "lib/$it" }

@ApiStatus.Internal
fun extractKotlincIdeScratchHome(distJar: Path, targetDir: Path): Path {
    val stampFile = targetDir.resolve(KOTLINC_IDE_SCRATCH_STAMP_FILE_NAME)
    val expectedStamp = buildString {
        appendLine(distJar.toAbsolutePath().normalize())
        appendLine(Files.size(distJar))
        appendLine(Files.getLastModifiedTime(distJar).toMillis())
    }
    val requiredFiles = kotlincIdeScratchHomeRelativePaths.map(targetDir::resolve)
    if (Files.isRegularFile(stampFile) && stampFile.readText() == expectedStamp && requiredFiles.all(Files::exists)) {
        return targetDir
    }
    if (Files.exists(targetDir)) {
        Files.walk(targetDir).sorted(Comparator.reverseOrder()).forEach(Files::delete)
    }
    Files.createDirectories(targetDir)
    val requiredZipEntries = kotlincIdeScratchHomeRelativePaths.toHashSet()
    Decompressor.Zip(distJar).filter(requiredZipEntries::contains).extract(targetDir)
    val missingFiles = requiredFiles.filterNot(Files::exists)
    check(missingFiles.isEmpty()) { "Scratch compiler artifacts are missing: ${missingFiles.joinToString()}" }
    stampFile.writeText(expectedStamp)
    return targetDir
}

@TestOnly
fun extractKotlincIdeScratchHomeForTests(distJar: Path, targetDir: Path): Path =
    extractKotlincIdeScratchHome(distJar = distJar, targetDir = targetDir)
