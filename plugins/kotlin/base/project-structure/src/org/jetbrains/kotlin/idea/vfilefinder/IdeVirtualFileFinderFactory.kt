// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.vfilefinder

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinder
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinderFactory

open class IdeVirtualFileFinderFactory(private val project: Project) : VirtualFileFinderFactory {
    override fun create(scope: GlobalSearchScope): VirtualFileFinder = IdeVirtualFileFinder(scope, project)

    override fun create(project: Project): VirtualFileFinder {
        // never called
        return IdeVirtualFileFinder(GlobalSearchScope.allScope(project), project)
    }
}
