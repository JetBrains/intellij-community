// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo

internal class NonUnderContentKtModuleFactory : KtModuleFactory {
    override fun createModule(moduleInfo: ModuleInfo): KtModule? = null

    override fun createModuleWithNonOriginalFile(
        moduleInfo: ModuleInfo,
        fakeFile: VirtualFile,
        originalFile: VirtualFile
    ): KtModule? = if (moduleInfo is ModuleSourceInfo) {
        KtSourceModuleByModuleInfoForNonUnderContentFile(fakeFile, originalFile, moduleInfo)
    } else {
        null
    }
}
