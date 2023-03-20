// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.scope

import com.intellij.openapi.module.impl.scopes.LibraryScopeBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile


// SearchScope may cache hashcode, see JavaDoc of com.intellij.psi.search.SearchScope.calcHashCode
@Suppress("EqualsOrHashCode")
internal class LibrarySourcesScope(
    project: Project,
    private val library: Library
) : LibraryScopeBase(project, VirtualFile.EMPTY_ARRAY, library.getFiles(OrderRootType.SOURCES)) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LibrarySourcesScope) return false
        return library == other.library
    }

    override fun calcHashCode(): Int {
        return 31 * super.calcHashCode() + library.hashCode()
    }
}
