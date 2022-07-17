// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.projectStructure.scope

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindMatcher
import org.jetbrains.kotlin.load.java.AbstractJavaClassFinder

@Suppress("EqualsOrHashCode") // DelegatingGlobalSearchScope requires to provide calcHashCode()
class KotlinSourceFilterScope private constructor(
    delegate: GlobalSearchScope,
    private val project: Project,
    private val filter: RootKindFilter
) : DelegatingGlobalSearchScope(delegate) {
    override fun getProject() = project

    override fun contains(file: VirtualFile): Boolean {
        val baseScope = this.myBaseScope

        if (baseScope is AbstractJavaClassFinder.FilterOutKotlinSourceFilesScope) {
            // KTIJ-20095: FilterOutKotlinSourceFilesScope optimization to avoid heavy file.fileType calculation
            val extension = file.extension
            val ktFile = when {
                file.isDirectory -> false
                extension == KotlinFileType.EXTENSION -> true
                extension == JavaFileType.DEFAULT_EXTENSION || extension == JavaClassFileType.INSTANCE.defaultExtension -> false
                else -> {
                    val fileTypeRegistry = FileTypeRegistry.getInstance()
                    val fileTypeByFileName = fileTypeRegistry.getFileTypeByFileName(file.name)

                    fileTypeByFileName == KotlinFileType.INSTANCE
                            || fileTypeByFileName == FileTypes.UNKNOWN && fileTypeRegistry.isFileOfType(file, KotlinFileType.INSTANCE)
                }
            }

            if (ktFile || !baseScope.base.contains(file)) return false
        } else if (!super.contains(file)) {
            return false
        }

        return RootKindMatcher.matches(project, file, filter)
    }

    override fun toString(): String {
        return "KotlinSourceFilterScope(delegate=$myBaseScope, filter=$filter)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        if (!super.equals(other)) return false

        other as KotlinSourceFilterScope

        if (filter != other.filter) return false
        if (project != other.project) return false

        return true
    }

    override fun calcHashCode(): Int {
        var result = super.calcHashCode()
        result = 31 * result + filter.hashCode()
        result = 31 * result + project.hashCode()
        return result
    }

    companion object {
        @JvmStatic
        fun everything(delegate: GlobalSearchScope, project: Project) =
            create(delegate, project, RootKindFilter.everything.copy(includeScriptsOutsideSourceRoots = true))

        @JvmStatic
        fun projectSourcesAndLibraryClasses(delegate: GlobalSearchScope, project: Project) =
            create(delegate, project, RootKindFilter.projectSourcesAndLibraryClasses.copy(includeScriptsOutsideSourceRoots = true))

        @JvmStatic
        fun projectFiles(delegate: GlobalSearchScope, project: Project) =
            create(delegate, project, RootKindFilter.projectFiles.copy(includeScriptsOutsideSourceRoots = true))

        @JvmStatic
        fun projectSources(delegate: GlobalSearchScope, project: Project) =
            create(delegate, project, RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = true))

        @JvmStatic
        fun librarySources(delegate: GlobalSearchScope, project: Project) =
            create(delegate, project, RootKindFilter.librarySources)

        @JvmStatic
        fun libraryClasses(delegate: GlobalSearchScope, project: Project) =
            create(delegate, project, RootKindFilter.libraryClasses)

        @JvmStatic
        fun projectAndLibrarySources(delegate: GlobalSearchScope, project: Project) =
            create(delegate, project, RootKindFilter.projectAndLibrarySourcesWithScripts.copy(includeScriptsOutsideSourceRoots = true))

        private fun create(
            delegate: GlobalSearchScope,
            project: Project,
            filter: RootKindFilter
        ): GlobalSearchScope {
            return when {
                delegate === GlobalSearchScope.EMPTY_SCOPE -> delegate
                delegate is KotlinSourceFilterScope -> KotlinSourceFilterScope(delegate.myBaseScope, project, filter)
                else -> KotlinSourceFilterScope(delegate, project, filter)
            }
        }
    }
}
