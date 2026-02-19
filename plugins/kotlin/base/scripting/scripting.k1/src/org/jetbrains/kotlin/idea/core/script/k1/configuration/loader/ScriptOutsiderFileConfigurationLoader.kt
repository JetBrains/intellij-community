// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k1.configuration.loader

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.util.getOutsiderFileOrigin
import org.jetbrains.kotlin.idea.core.script.k1.configuration.cache.ScriptConfigurationSnapshot
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

internal class ScriptOutsiderFileConfigurationLoader(val project: Project) :
    ScriptConfigurationLoader {
    override fun loadDependencies(
        isFirstLoad: Boolean,
        ktFile: KtFile,
        scriptDefinition: ScriptDefinition,
        context: ScriptConfigurationLoadingContext
    ): Boolean {
        if (!isFirstLoad) return false

        val virtualFile = ktFile.originalFile.virtualFile
        val fileOrigin = getOutsiderFileOrigin(project, virtualFile) ?: return false

        val original = context.getCachedConfiguration(fileOrigin)
        if (original != null) {
            context.saveNewConfiguration(
                virtualFile,
                ScriptConfigurationSnapshot(
                    original.inputs,
                    listOf(),
                    original.configuration
                )
            )
        }

        // todo(KT-34615): initiate loading configuration for original file and subscribe to it's result?
        //                 should we show "new context" notification for both files?

        return true
    }
}