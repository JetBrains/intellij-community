// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.idea.base.scripting.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.KotlinSupportAvailability
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.psi.KtFile

class ScriptingSupportAvailability: KotlinSupportAvailability {
    override fun name(): String = KotlinBaseScriptingBundle.message("scripting.support.availability.name")

    override fun isSupported(ktFile: KtFile): Boolean =
        Registry.`is`("kotlin.k2.scripting.enabled", true) ||
                !KotlinScriptingSettings.getInstance(ktFile.project).showK2SupportWarning ||
                !ktFile.isScript()
}