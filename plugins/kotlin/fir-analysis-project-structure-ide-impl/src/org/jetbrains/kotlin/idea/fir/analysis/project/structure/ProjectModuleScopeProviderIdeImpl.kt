// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.analysis.project.structure

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.project.structure.KtModuleScopeProvider
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule

internal class KtModuleScopeProviderIdeImpl : KtModuleScopeProvider() {
    override fun getModuleLibrariesScope(sourceModule: KtSourceModule): GlobalSearchScope {
        return ModuleLibrariesSearchScope(sourceModule.ideaModule)
    }
}