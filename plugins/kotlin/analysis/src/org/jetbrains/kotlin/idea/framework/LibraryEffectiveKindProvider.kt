// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.framework

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import org.jetbrains.kotlin.idea.util.application.getServiceSafe

interface LibraryEffectiveKindProvider {
    fun getEffectiveKind(library: LibraryEx): PersistentLibraryKind<*>?

    companion object {
        fun getInstance(project: Project): LibraryEffectiveKindProvider = project.getServiceSafe()
    }
}

fun LibraryEx.effectiveKind(project: Project) = LibraryEffectiveKindProvider.getInstance(project).getEffectiveKind(this)