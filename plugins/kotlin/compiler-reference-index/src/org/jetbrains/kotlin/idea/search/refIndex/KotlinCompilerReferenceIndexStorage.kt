// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.name.FqName

/**
 * Interface responsible for managing the storage of the Kotlin Compiler Reference Index (KCRI).
 * Implementations can choose how the data is retrieved from the CRI data.
 * Typically there should be one implementation for each build system that wants to offer CRI capabilities.
  */
@ApiStatus.Experimental
interface KotlinCompilerReferenceIndexStorage {
    /**
     * Returns the list of [VirtualFile]s that make use of the given [fqName].
     */
    fun getUsages(fqName: FqName): List<VirtualFile>

    /**
     * Returns subtypes of the class/interface identified by [fqName] stored within this storage.
     * When [deep] is true, the returned [Sequence] represents the transitive closure over the subtype relation.
     */
    fun getSubtypesOf(fqName: FqName, deep: Boolean): Sequence<FqName>

    /**
     * Closes the storage and releases any held resources.
     */
    fun close()

    companion object {
        /**
         * Opens a storage for the given [project] by finding the first [KotlinCompilerReferenceIndexStorageProvider]
         * that is applicable for the [project].
         * Returns null if no provider is applicable or if storage creation fails (e.g. index missing or corrupted).
         */
        internal fun open(project: Project, projectPath: String): KotlinCompilerReferenceIndexStorage? {
            val provider = KotlinCompilerReferenceIndexStorageProvider.getApplicableProvider(project) ?: return null
            return provider.createStorage(project, projectPath)
        }

        /**
         *  Returns true if the first applicable [KotlinCompilerReferenceIndexStorageProvider] reports that an index exists for [project].
         */
        internal fun hasIndex(project: Project): Boolean {
            return KotlinCompilerReferenceIndexStorageProvider.getApplicableProvider(project)?.hasIndex(project) == true
        }
    }
}