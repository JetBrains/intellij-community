// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiPlatform.java

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.java.KotlinJavaModulePsiAnnotationsProvider
import org.jetbrains.kotlin.idea.vfilefinder.IdeVirtualFileFinder
import org.jetbrains.kotlin.name.ClassId

internal class IdeKotlinJavaModuleAnnotationsProvider(private val project: Project) : KotlinJavaModulePsiAnnotationsProvider {
    private val virtualFileFinder by lazy {
        IdeVirtualFileFinder(GlobalSearchScope.allScope(project), project)
    }

    override fun getAnnotationsForModuleOwnerOfClass(classId: ClassId): List<PsiAnnotation>? {
        val virtualFile = virtualFileFinder.findVirtualFileWithHeader(classId) ?: return null
        return project.findJavaModule(virtualFile)?.annotations?.toList<PsiAnnotation>()
    }
}
