// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.util

import org.jetbrains.kotlin.idea.base.projectStructure.LibraryDependenciesCache
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus.checkCanceled

fun LibraryDependenciesCache.getTransitiveLibraryDependencyInfos(libraryInfo: LibraryInfo): Collection<LibraryInfo> =
    getTransitiveLibraryDependencyInfos(getLibraryDependencies(libraryInfo).libraries)

fun LibraryDependenciesCache.getTransitiveLibraryDependencyInfos(libraryInfos: Collection<LibraryInfo>): Collection<LibraryInfo> =
    libraryInfos.closure { libraryDependency ->
        checkCanceled()
        getLibraryDependencies(libraryDependency).libraries
    }

private fun <T> Collection<T>.closure(f: (T) -> Collection<T>): Collection<T> {
    if (isEmpty()) return this

    val result = HashSet(this)
    var elementsToCheck = result
    var oldSize = 0
    while (result.size > oldSize) {
        oldSize = result.size
        val toAdd = hashSetOf<T>()
        elementsToCheck.forEach { toAdd.addAll(f(it)) }
        result.addAll(toAdd)
        elementsToCheck = toAdd
    }

    return result
}
