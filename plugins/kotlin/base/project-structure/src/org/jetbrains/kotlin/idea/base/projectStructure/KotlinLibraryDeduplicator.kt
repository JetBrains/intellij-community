// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library

abstract class KotlinLibraryDeduplicator {
    abstract fun deduplicatedLibrary(library: Library): Library

    companion object {
        fun getInstance(project: Project): KotlinLibraryDeduplicator =
            project.service<KotlinLibraryDeduplicator>()
    }
}