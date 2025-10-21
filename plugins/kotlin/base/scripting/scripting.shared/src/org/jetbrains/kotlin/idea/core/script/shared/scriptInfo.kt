// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.shared

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import java.lang.ref.Reference

abstract class LightScriptInfo() {
    @Volatile
    var heavyCache: Reference<HeavyScriptInfo>? = null

    abstract fun buildConfiguration(): ScriptCompilationConfigurationWrapper?
}

class DirectScriptInfo(val result: ScriptCompilationConfigurationWrapper) : LightScriptInfo() {
    override fun buildConfiguration(): ScriptCompilationConfigurationWrapper = result
}

class HeavyScriptInfo(
    val scriptConfiguration: ScriptCompilationConfigurationWrapper,
    val classFiles: List<VirtualFile>,
    val classFilesScope: GlobalSearchScope,
    val sdk: Sdk?
)