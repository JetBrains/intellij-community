// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.kotlin.idea.klib.AbstractKlibLibraryInfo
import org.jetbrains.kotlin.platform.TargetPlatform

internal sealed class LibraryDependencyCandidate {
    abstract val platform: TargetPlatform
    abstract val libraries: List<LibraryInfo>

    companion object {
        fun fromLibraryOrNull(project: Project, library: Library): LibraryDependencyCandidate? {
            val libraryInfos = createLibraryInfo(project, library)
            val libraryInfo = libraryInfos.firstOrNull() ?: return null
            if(libraryInfo is AbstractKlibLibraryInfo) {
                return KlibLibraryDependencyCandidate(
                    platform = libraryInfo.platform,
                    libraries = libraryInfos,
                    uniqueName = libraryInfo.uniqueName,
                    isInterop = libraryInfo.isInterop
                )
            }

            return DefaultLibraryDependencyCandidate(
                platform = libraryInfo.platform,
                libraries = libraryInfos
            )
        }
    }
}

internal data class DefaultLibraryDependencyCandidate(
    override val platform: TargetPlatform,
    override val libraries: List<LibraryInfo>
): LibraryDependencyCandidate()

internal data class KlibLibraryDependencyCandidate(
    override val platform: TargetPlatform,
    override val libraries: List<LibraryInfo>,
    val uniqueName: String?,
    val isInterop: Boolean
): LibraryDependencyCandidate()


