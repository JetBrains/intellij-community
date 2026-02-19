// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Provider for [KotlinCompilerReferenceIndexStorage] implementations.
 * The provider is responsible for checking whether it is applicable for a given project and, if it is the case,
 * for creating a [KotlinCompilerReferenceIndexStorage] instance.
 *
 * Typically, there should be one implementation per build system that wants to offer Kotlin CRI capabilities.
 *
 * Implementations can register themselves via the `org.jetbrains.kotlin.kotlinCompilerReferenceIndexStorageProvider` EP.
 */
@ApiStatus.Experimental
interface KotlinCompilerReferenceIndexStorageProvider {
    /**
     * Returns true if this provider can operate in the given [project] environment.
     *
     * The method should be fast and side‑effect free. It is called on the UI thread during provider selection.
     */
    fun isApplicable(project: Project): Boolean

    /**
     * Returns true iff the underlying index required by this provider exists for the given [project].
     * Implementations should avoid heavy I/O here; a cheap existence/corruption check is sufficient.
     */
    fun hasIndex(project: Project): Boolean

    /**
     * Creates a new storage instance or returns null if storage cannot be created (e.g., index absent/corrupted).
     *
     * The [projectPath] is a base directory used by implementations that rely on relative paths persisted in the
     * index. Implementations should perform any required light‑weight initialization here and delay heavy work if
     * possible. The returned storage must be closed by the caller.
     */
    fun createStorage(project: Project, projectPath: String): KotlinCompilerReferenceIndexStorage?


    companion object {
        val EP_NAME: ExtensionPointName<KotlinCompilerReferenceIndexStorageProvider> =
            ExtensionPointName.create("org.jetbrains.kotlin.kotlinCompilerReferenceIndexStorageProvider")

        /**
         * Returns the first provider whose [isApplicable] returns true according to the EP registration order.
         *
         * To change precedence, adjust the `order` attribute in `intellij.kotlin.compilerReferenceIndex.xml`.
         */
        fun getApplicableProvider(project: Project): KotlinCompilerReferenceIndexStorageProvider? =
            EP_NAME.extensionList.firstOrNull { it.isApplicable(project) }
    }
}