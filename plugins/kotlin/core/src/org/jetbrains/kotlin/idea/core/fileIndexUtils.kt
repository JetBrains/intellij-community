// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core

import com.intellij.openapi.roots.FileIndex
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.config.ResourceKotlinRootType
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestResourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.idea.caches.project.SourceType
import org.jetbrains.kotlin.idea.util.isInSourceContentWithoutInjected

private val testRootTypes: Set<JpsModuleSourceRootType<*>> = setOf(
    JavaSourceRootType.TEST_SOURCE,
    JavaResourceRootType.TEST_RESOURCE,
    TestSourceKotlinRootType,
    TestResourceKotlinRootType
)

private val sourceRootTypes = setOf<JpsModuleSourceRootType<*>>(
    JavaSourceRootType.SOURCE,
    JavaResourceRootType.RESOURCE,
    SourceKotlinRootType,
    ResourceKotlinRootType
)

fun JpsModuleSourceRootType<*>.getSourceType(): SourceType? = when (this) {
    in sourceRootTypes -> SourceType.PRODUCTION
    in testRootTypes -> SourceType.TEST
    else -> null
}

fun FileIndex.isInTestSourceContentKotlinAware(fileOrDir: VirtualFile) = isUnderSourceRootOfType(fileOrDir, testRootTypes)

fun FileIndex.getSourceType(fileOrDir: VirtualFile): SourceType? = when {
    isInTestSourceContentKotlinAware(fileOrDir) -> SourceType.TEST
    isInSourceContentWithoutInjected(fileOrDir) -> SourceType.PRODUCTION
    else -> null
}