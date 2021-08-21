// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.analysis.providers.ide

import org.jetbrains.kotlin.analysis.providers.KotlinModuleInfoProvider
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.idea.caches.project.getModuleInfo
import com.intellij.psi.PsiElement

internal class KotlinIdeModuleInfoProvider : KotlinModuleInfoProvider() {
    override fun getModuleInfo(element: KtElement): ModuleInfo {
       return (element as PsiElement).getModuleInfo()
    }
}