// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1.ucache

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.core.script.shared.LightScriptInfo
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper

@K1Deprecation
class DirectScriptInfo(val result: ScriptCompilationConfigurationWrapper) : LightScriptInfo() {
    override fun buildConfiguration(): ScriptCompilationConfigurationWrapper = result
}