// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches

@Deprecated("Use 'org.jetbrains.kotlin.idea.base.projectStructure.RootKindMatcher' instead")
object ProjectRootsUtil {
    @JvmOverloads
    @JvmStatic
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Use 'org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter.projectAndLibrarySources' instead")
    fun isInProjectOrLibSource(element: PsiElement, includeScriptsOutsideSourceRoots: Boolean = false): Boolean {
        return RootKindFilter.projectAndLibrarySources
            .copy(includeScriptsOutsideSourceRoots = includeScriptsOutsideSourceRoots)
            .matches(element)
    }
}