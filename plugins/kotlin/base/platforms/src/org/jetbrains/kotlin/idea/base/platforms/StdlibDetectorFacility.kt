// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.platforms

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion

sealed class StdlibDetectorFacility {
    abstract fun getStdlibJar(roots: List<VirtualFile>): VirtualFile?

    protected abstract val supportedLibraryKind: KotlinLibraryKind?

    fun isStdlib(project: Project, library: Library, ignoreKind: Boolean = false): Boolean {
        if (library !is LibraryEx || library.isDisposed) {
            return false
        }

        if (!ignoreKind && !isSupported(project, library)) {
            return false
        }

        val classes = listOf(*library.getFiles(OrderRootType.CLASSES))
        return getStdlibJar(classes) != null
    }

    fun getStdlibVersion(roots: List<VirtualFile>): IdeKotlinVersion? {
        val stdlibJar = getStdlibJar(roots) ?: return null
        return IdeKotlinVersion.fromManifest(stdlibJar)
    }

    fun getStdlibVersion(project: Project, library: Library): IdeKotlinVersion? {
        if (library !is LibraryEx || library.isDisposed || !isSupported(project, library)) {
            return null
        }

        return getStdlibVersion(library.getFiles(OrderRootType.CLASSES).asList())
    }

    protected fun isSupported(project: Project, library: LibraryEx): Boolean {
        return project.service<LibraryEffectiveKindProvider>().getEffectiveKind(library) == supportedLibraryKind
    }
}