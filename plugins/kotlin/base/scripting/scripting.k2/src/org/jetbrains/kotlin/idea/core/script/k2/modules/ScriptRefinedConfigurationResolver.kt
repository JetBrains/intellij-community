// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.k2.configurations.ScriptConfigurationWithSdk
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

interface ScriptRefinedConfigurationResolver {
    suspend fun create(virtualFile: VirtualFile, definition: ScriptDefinition): ScriptConfigurationWithSdk?
    fun get(virtualFile: VirtualFile): ScriptConfigurationWithSdk?
    fun remove(virtualFile: VirtualFile): Unit = Unit
}