// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope as NewKotlinSourceFilterScope

@Deprecated("Use 'org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope' instead")
class KotlinSourceFilterScope private constructor() {
    companion object {
        @Deprecated(
            "Use 'org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope.projectSourcesAndLibraryClasses()' instead.",
            ReplaceWith(
                "KotlinSourceFilterScope.projectSourcesAndLibraryClasses(delegate, project)",
                "org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope"
            )
        )
        @JvmStatic
        fun projectSourceAndClassFiles(delegate: GlobalSearchScope, project: Project): GlobalSearchScope =
            NewKotlinSourceFilterScope.projectSourcesAndLibraryClasses(delegate, project)

        @Deprecated(
            "Use 'org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope.projectSources()' instead.",
            ReplaceWith(
                "KotlinSourceFilterScope.projectSources(delegate, project)",
                "org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope"
            )
        )
        @JvmStatic
        fun projectSources(delegate: GlobalSearchScope, project: Project): GlobalSearchScope =
            NewKotlinSourceFilterScope.projectSources(delegate, project)

        @Deprecated(
            "Use 'org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope.librarySources()' instead.",
            ReplaceWith(
                "KotlinSourceFilterScope.librarySources(delegate, project)",
                "org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope"
            )
        )
        @JvmStatic
        fun librarySources(delegate: GlobalSearchScope, project: Project): GlobalSearchScope =
            NewKotlinSourceFilterScope.librarySources(delegate, project)
    }
}