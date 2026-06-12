// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KtCompilerFacilityUtils")

package org.jetbrains.kotlin.idea.base.codeInsight.compiler

import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaCompilationOptions
import org.jetbrains.kotlin.analysis.api.components.KaCompilationResult
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnostic
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createParentDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.writeBytes

interface KotlinCompilerIdeAllowedErrorFilter : (KaDiagnostic) -> Boolean {
    companion object {
        fun getInstance(): KotlinCompilerIdeAllowedErrorFilter = service()
    }
}

@KaExperimentalApi
@ApiStatus.Internal
fun KaSession.compileToDirectory(file: KtFile, options: KaCompilationOptions, destination: Path): KaCompilationResult {
    val result = compile(file, options)
    if (result is KaCompilationResult.Success) {
        for (outputFile in result.output) {
            val target = destination.resolve(outputFile.path)
            target.createParentDirectories()
            target.writeBytes(outputFile.content)
        }
    }
    return result
}

@KaExperimentalApi
@ApiStatus.Internal
fun KaSession.compileToJar(file: KtFile, options: KaCompilationOptions, destination: Path): KaCompilationResult {
    val result = compile(file, options)
    if (result is KaCompilationResult.Success) {
        destination.outputStream().buffered().use { os ->
            ZipOutputStream(os).use { zos ->
                for (outputFile in result.output) {
                    zos.putNextEntry(ZipEntry(outputFile.path))
                    zos.write(outputFile.content)
                    zos.closeEntry()
                }
            }
        }
    }
    return result
}