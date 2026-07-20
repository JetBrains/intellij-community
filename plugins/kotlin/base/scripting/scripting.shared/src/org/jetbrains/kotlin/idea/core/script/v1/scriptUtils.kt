// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.v1

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.kotlin.psi.KtFile
import kotlin.script.experimental.api.ScriptDiagnostic

fun indexSourceRootsEagerly(): Boolean = Registry.`is`("kotlin.scripting.index.dependencies.sources", false)

val KtFile.alwaysVirtualFile: VirtualFile get() = originalFile.virtualFile ?: viewProvider.virtualFile

fun loggingReporter(severity: ScriptDiagnostic.Severity, message: String) {
    when (severity) {
        ScriptDiagnostic.Severity.FATAL, ScriptDiagnostic.Severity.ERROR -> {
            kotlinScriptLogger.error(message)
        }

        ScriptDiagnostic.Severity.WARNING, ScriptDiagnostic.Severity.INFO -> {
            kotlinScriptLogger.info(message)
        }

        else -> {}
    }
}

fun Project.getKtFile(virtualFile: VirtualFile?, ktFile: KtFile? = null): KtFile? {
    if (virtualFile == null) return null
    if (ktFile != null) {
        check(ktFile.originalFile.virtualFile == virtualFile)
        return ktFile
    } else {
        return runReadAction { PsiManager.getInstance(this).findFile(virtualFile) as? KtFile }
    }
}

suspend fun Project.awaitExternalSystemInitialization() {
    suspendCancellableCoroutine { continuation ->
        ExternalProjectsManagerImpl.getInstance(this).runWhenInitialized {
            continuation.resumeWith(Result.success(Unit))
        }
    }
}