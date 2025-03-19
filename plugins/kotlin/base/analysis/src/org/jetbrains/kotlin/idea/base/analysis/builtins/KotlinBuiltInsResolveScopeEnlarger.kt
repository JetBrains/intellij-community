// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.builtins

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ResolveScopeEnlarger
import com.intellij.psi.search.SearchScope
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltinsVirtualFileProvider
import org.jetbrains.kotlin.idea.base.facet.platform.platform

/**
 * Enlarges resolve scope such that it includes bundled builtins for multi-platform common modules.
 *
 * @see org.jetbrains.kotlin.idea.base.indices.contributors.BuiltInsIndexableSetContributor
 */
class KotlinBuiltInsResolveScopeEnlarger : ResolveScopeEnlarger() {
    override fun getAdditionalResolveScope(
        file: VirtualFile,
        project: Project
    ): SearchScope? {
        val module = ProjectFileIndex.getInstance(project).getModuleForFile(file) ?: return null
        if (!module.platform.hasCommonKotlinStdlib()) return null
        return BuiltinsVirtualFileProvider.getInstance().createBuiltinsScope(project)
    }
}