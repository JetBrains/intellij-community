// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo

@Deprecated("Use 'LibraryDependenciesCache.LibraryDependencies' instead.")
internal typealias LibrariesAndSdks = Pair<List<LibraryInfo>, List<SdkInfo>>

interface LibraryDependenciesCache {
    companion object {
        fun getInstance(project: Project): LibraryDependenciesCache = project.service()
    }

    @Deprecated(
        "Use 'getLibraryDependencies()' instead.",
        ReplaceWith("getLibraryDependencies(libraryInfo).let { it.libraries to it.sdk }")
    )
    fun getLibrariesAndSdksUsedWith(libraryInfo: LibraryInfo): LibrariesAndSdks {
        return getLibraryDependencies(libraryInfo).let { it.libraries to it.sdk }
    }

    fun getLibraryDependencies(library: LibraryInfo): LibraryDependencies

    class LibraryDependencies(val library: LibraryInfo, val libraries: List<LibraryInfo>, val sdk: List<SdkInfo>) {
        val librariesWithoutSelf: List<LibraryInfo> by lazy { libraries - library }
    }
}