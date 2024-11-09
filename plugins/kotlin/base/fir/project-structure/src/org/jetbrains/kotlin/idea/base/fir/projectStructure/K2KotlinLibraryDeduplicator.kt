// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinLibraryDeduplicator
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi

internal class K2KotlinLibraryDeduplicator(private val project: Project) : KotlinLibraryDeduplicator() {
    override fun deduplicatedLibrary(library: Library): Library {
        @OptIn(K1ModeProjectStructureApi::class)
        // as a migration phase (KTIJ-31684), we still need the deduplicator for K2
        return LibraryInfoCache.getInstance(project).deduplicatedLibrary(library)
    }
}