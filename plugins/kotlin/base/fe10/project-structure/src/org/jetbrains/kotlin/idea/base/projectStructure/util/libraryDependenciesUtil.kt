// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.util

import org.jetbrains.kotlin.idea.base.projectStructure.LibraryDependenciesCache
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus
import org.jetbrains.kotlin.utils.closure


fun LibraryDependenciesCache.getTransitiveLibraryDependencyInfos(libraryInfo: LibraryInfo): Collection<LibraryInfo> =
    getTransitiveLibraryDependencyInfos(getLibraryDependencies(libraryInfo).libraries)

fun LibraryDependenciesCache.getTransitiveLibraryDependencyInfos(libraryInfos: Collection<LibraryInfo>): Collection<LibraryInfo> =
    libraryInfos.closure { libraryDependency ->
        ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()
        getLibraryDependencies(libraryDependency).libraries
    }