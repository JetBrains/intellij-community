// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.shared

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.UserDataProperty
import kotlin.script.experimental.api.ScriptDiagnostic

fun getScriptReports(file: VirtualFile): List<ScriptDiagnostic> {
    return file.scriptDiagnostics ?: emptyList()
}

fun getScriptReports(file: KtFile): List<ScriptDiagnostic> {
    return file.originalFile.virtualFile?.scriptDiagnostics ?: emptyList()
}

var VirtualFile.scriptDiagnostics: List<ScriptDiagnostic>? by UserDataProperty(Key.create("KOTLIN_SCRIPT_DIAGNOSTICS"))