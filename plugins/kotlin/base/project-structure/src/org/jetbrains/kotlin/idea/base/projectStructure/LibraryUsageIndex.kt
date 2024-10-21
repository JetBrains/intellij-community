// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi

/**
 * For each [LibraryInfo], [LibraryUsageIndex] contains all [Module]s which depend on that library info. The index supports deduplicated
 * libraries: if a [LibraryInfo] comprises two [Library] instances with the same roots, [getDependentModules] will return the union of both
 * [Library]'s dependents.
 *
 * The resulting dependents are stable and do not depend on the state of [LibraryInfoCache], as they are computed from the project model.
 */
@K1ModeProjectStructureApi
interface LibraryUsageIndex {
    fun getDependentModules(libraryInfo: LibraryInfo): Sequence<Module>
    fun hasDependentModule(libraryInfo: LibraryInfo, module: Module): Boolean
}
