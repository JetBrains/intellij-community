// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KtCompilerFacilityUtils")

package org.jetbrains.kotlin.idea.base.codeInsight.compiler

import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.components.KtCompilationResult
import org.jetbrains.kotlin.analysis.api.components.KtCompilerFacilityMixIn
import org.jetbrains.kotlin.analysis.api.components.KtCompilerTarget
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

interface KotlinCompilerIdeAllowedErrorFilter : (KtDiagnostic) -> Boolean {
    companion object {
        fun getInstance(): KotlinCompilerIdeAllowedErrorFilter = service()
    }
}

@ApiStatus.Internal
fun KtCompilerFacilityMixIn.compileToDirectory(
    file: KtFile,
    configuration: CompilerConfiguration,
    target: KtCompilerTarget,
    allowedErrorFilter: (KtDiagnostic) -> Boolean,
    destination: File
): KtCompilationResult {
    val result = compile(file, configuration, target, allowedErrorFilter)
    if (result is KtCompilationResult.Success) {
        for (outputFile in result.output) {
            val target = File(destination, outputFile.path)
            (target.parentFile ?: error("Can't find parent for file $target")).mkdirs()
            target.writeBytes(outputFile.content)
        }
    }
    return result
}

@ApiStatus.Internal
fun KtCompilerFacilityMixIn.compileToJar(
    file: KtFile,
    configuration: CompilerConfiguration,
    target: KtCompilerTarget,
    allowedErrorFilter: (KtDiagnostic) -> Boolean,
    destination: File
): KtCompilationResult {
    val result = compile(file, configuration, target, allowedErrorFilter)
    if (result is KtCompilationResult.Success) {
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