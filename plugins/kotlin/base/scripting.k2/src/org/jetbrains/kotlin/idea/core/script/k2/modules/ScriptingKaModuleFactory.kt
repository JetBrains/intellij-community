// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(K1ModeProjectStructureApi::class, KaPlatformInterface::class)

package org.jetbrains.kotlin.idea.core.script.k2.modules

import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.KaModuleFactory
import org.jetbrains.kotlin.idea.base.projectStructure.KtScriptLibraryModuleByModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.KtScriptLibrarySourceModuleByModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.JvmLibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibrarySourceInfo
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource

internal class ScriptingKaModuleFactory : KaModuleFactory {
    override fun createModule(moduleInfo: ModuleInfo): KaModule? {
        return when (moduleInfo) {
            is JvmLibraryInfo -> (moduleInfo.source as? KotlinScriptEntitySource)?.let {
                KtScriptLibraryModuleByModuleInfo(moduleInfo)
            }
            is LibrarySourceInfo -> (moduleInfo.source as? KotlinScriptEntitySource)?.let {
                KtScriptLibrarySourceModuleByModuleInfo(moduleInfo)
            }
            else -> null
        }
    }

}
