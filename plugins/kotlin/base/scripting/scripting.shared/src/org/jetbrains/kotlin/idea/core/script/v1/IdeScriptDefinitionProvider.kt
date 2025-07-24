// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.v1

import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.scripting.definitions.LazyScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider

@InternalIgnoreDependencyViolation
abstract class IdeScriptDefinitionProvider : LazyScriptDefinitionProvider() {
    abstract fun getDefinitions(): List<ScriptDefinition>

    companion object {
        fun getInstance(project: Project): IdeScriptDefinitionProvider {
            return project.service<ScriptDefinitionProvider>() as IdeScriptDefinitionProvider
        }
    }
}