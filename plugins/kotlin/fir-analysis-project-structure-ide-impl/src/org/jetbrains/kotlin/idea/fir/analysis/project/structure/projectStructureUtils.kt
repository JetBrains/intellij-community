// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.analysis.project.structure

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.idea.caches.project.IdeaModuleInfo
import org.jetbrains.kotlin.idea.caches.project.productionSourceInfo


@RequiresOptIn("Transitional API for access FE1.0 compiler-relate stuff which will be removed soon")
annotation class FE10ApiUsage

@FE10ApiUsage
val KtModule.moduleInfo: IdeaModuleInfo
    get() {
        require(this is KtSourceModuleByModuleInfo)
        return ideaModuleInfo
    }


val KtSourceModule.ideaModule: Module
    get() {
        require(this is KtSourceModuleByModuleInfo)
        return ideaModule
    }

fun Module.getMainKtSourceModule(): KtSourceModule? {
    val moduleInfo = productionSourceInfo() ?: return null
    return ProjectStructureProviderIdeImpl.getInstance(project).getKtModuleByModuleInfo(moduleInfo) as KtSourceModule
}