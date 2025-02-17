// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinLibraryDeduplicator

internal class K2KotlinLibraryDeduplicator() : KotlinLibraryDeduplicator() {
    override fun deduplicatedLibrary(library: Library): Library {
        return library
    }
}