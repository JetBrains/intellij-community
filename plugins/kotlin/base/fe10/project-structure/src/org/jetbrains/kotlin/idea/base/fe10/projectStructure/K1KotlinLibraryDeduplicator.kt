// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.projectStructure

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinLibraryDeduplicator
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache

internal class K1KotlinLibraryDeduplicator(private val project: Project) : KotlinLibraryDeduplicator() {
    override fun deduplicatedLibrary(library: Library): Library {
        return LibraryInfoCache.getInstance(project).deduplicatedLibrary(library)
    }
}