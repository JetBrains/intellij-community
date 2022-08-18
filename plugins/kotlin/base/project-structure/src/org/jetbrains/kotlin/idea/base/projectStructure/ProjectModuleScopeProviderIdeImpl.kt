// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.project.structure.KtModuleScopeProvider
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.scope.ModuleLibrariesSearchScope

internal class KtModuleScopeProviderIdeImpl : KtModuleScopeProvider() {
    override fun getModuleLibrariesScope(sourceModule: KtSourceModule): GlobalSearchScope {
        return ModuleLibrariesSearchScope(sourceModule.ideaModule)
    }
}