// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KtCompilerFacilityUtils")

package org.jetbrains.kotlin.idea.base.codeInsight.compiler

import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaCompilationResult
import org.jetbrains.kotlin.analysis.api.components.KaCompilerTarget
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnostic
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

interface KotlinCompilerIdeAllowedErrorFilter : (KaDiagnostic) -> Boolean {
    companion object {
        fun getInstance(): KotlinCompilerIdeAllowedErrorFilter = service()
    }
}

@KaExperimentalApi
@ApiStatus.Internal
fun KaSession.compileToDirectory(
    file: KtFile,
    configuration: CompilerConfiguration,
    target: KaCompilerTarget,
    allowedErrorFilter: (KaDiagnostic) -> Boolean,
    destination: File
): KaCompilationResult {
    val result = compile(file, configuration, target, allowedErrorFilter)
    if (result is KaCompilationResult.Success) {
        for (outputFile in result.output) {
            val target = File(destination, outputFile.path)
            (target.parentFile ?: error("Can't find parent for file $target")).mkdirs()
            target.writeBytes(outputFile.content)
        }
    }
    return result
}

@KaExperimentalApi
@ApiStatus.Internal
fun KaSession.compileToJar(
    file: KtFile,
    configuration: CompilerConfiguration,
    target: KaCompilerTarget,
    allowedErrorFilter: (KaDiagnostic) -> Boolean,
    destination: File
): KaCompilationResult {
    val result = compile(file, configuration, target, allowedErrorFilter)
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