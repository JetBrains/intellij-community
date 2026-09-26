// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.openapi.module.Module

/**
 * Optional capability for [KotlinCompilerReferenceIndexStorage] implementations that can update data
 * incrementally for a subset of updated project modules without recreating the whole storage.
 */
internal interface IncrementalKotlinCompilerReferenceIndexStorage {
    /**
     * Refreshes KCRI data for [modules].
     *
     * @return true when storage remains valid after refresh, false when caller should recreate storage from scratch.
     */
    fun refreshModules(modules: Collection<Module>): Boolean
}
