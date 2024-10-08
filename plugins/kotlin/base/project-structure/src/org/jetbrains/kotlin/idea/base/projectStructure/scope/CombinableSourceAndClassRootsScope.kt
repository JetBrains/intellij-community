// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.scope

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import it.unimi.dsi.fastutil.objects.Object2IntMap

/**
 * A root-based [GlobalSearchScope] which can be combined into a [CombinedSourceAndClassRootsScope].
 */
interface CombinableSourceAndClassRootsScope {
    /**
     * A map from [VirtualFile] roots to an integer which represents the position of the root in the classpath.
     */
    val roots: Object2IntMap<VirtualFile>

    /**
     * All modules covered by this scope.
     *
     * Not all roots need to correspond to one of these modules, as the scope may also contain library class roots.
     */
    val modules: Set<Module>

    /**
     * Whether the scope contains a root which originated from a library.
     */
    val includesLibraryRoots: Boolean
}

/**
 * Returns a new list of [roots] ordered by their position in the classpath.
 */
fun CombinableSourceAndClassRootsScope.getOrderedRoots(): List<VirtualFile> = roots.keys.sortedBy(roots::getInt)
