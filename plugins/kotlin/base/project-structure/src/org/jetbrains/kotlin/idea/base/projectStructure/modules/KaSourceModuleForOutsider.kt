// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.modules

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.idea.base.util.minus

/**
 * A [KaSourceModule] for a file that does not directly belong to a project, serving as a substitution for an existing project source file.
 *
 * The original file is represented by [originalVirtualFile], and the substitution file is represented by [fakeVirtualFile].
 *
 * A good example of an outsider file is a file in the IntelliJ IDEA diff window, showing a previous revision.
 *
 * @see com.intellij.codeInsight.daemon.OutsidersPsiFileSupport
 */
@ApiStatus.Internal
interface KaSourceModuleForOutsider : KaSourceModule {
    val fakeVirtualFile: VirtualFile
    val originalVirtualFile: VirtualFile?

    fun adjustContentScope(scope: GlobalSearchScope): GlobalSearchScope {
        val scopeWithFakeFile = GlobalSearchScope.fileScope(project, fakeVirtualFile).uniteWith(scope)

        return if (originalVirtualFile != null) {
            scopeWithFakeFile.minus(GlobalSearchScope.fileScope(project, originalVirtualFile))
        } else {
            scopeWithFakeFile
        }
    }
}