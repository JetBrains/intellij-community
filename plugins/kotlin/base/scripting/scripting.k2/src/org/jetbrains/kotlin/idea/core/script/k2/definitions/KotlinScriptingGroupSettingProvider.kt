// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle

internal class KotlinScriptingGroupSettingProvider : CodeVisionGroupSettingProvider {
    companion object {
        const val GROUP_ID = "kotlin.script"
    }

    override val groupId: String = GROUP_ID

    override val groupName: String
        get() = KotlinBaseScriptingBundle.message("codeLens.KotlinScriptDefinitionCodeVisionProvider.name")

    override val description: String
        get() = KotlinBaseScriptingBundle.message("codeLens.KotlinScriptDefinitionCodeVisionProvider.description")
}
