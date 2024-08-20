// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.idea.base.scripting.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.KotlinSupportAvailability
import org.jetbrains.kotlin.idea.core.script.k2.K2ScriptDefinitionProvider
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource

class ScriptingSupportAvailability : KotlinSupportAvailability {
    override fun name(): String = KotlinBaseScriptingBundle.message("scripting.support.availability.name")

    override fun isSupported(ktElement: KtElement): Boolean {
        val ktFile = ktElement.containingFile as? KtFile ?: return true
        if (!ktFile.isScript()) return true

        return Registry.`is`("kotlin.k2.scripting.enabled", true) ||
                !KotlinScriptingSettings.getInstance(ktFile.project).showK2SupportWarning
    }
}