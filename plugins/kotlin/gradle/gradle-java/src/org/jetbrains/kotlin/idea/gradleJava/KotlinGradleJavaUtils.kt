// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinGradleJavaUtils")

package org.jetbrains.kotlin.idea.gradleJava

import com.intellij.openapi.externalSystem.model.project.ContentRootData
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.projectStructure.getMigratedSourceRootTypeWithProperties
import org.jetbrains.kotlin.idea.framework.KotlinSdkType

val ContentRootData.SourceRoot.pathAsUrl
    @ApiStatus.Internal get() = VirtualFileManager.constructUrl(URLUtil.FILE_PROTOCOL, FileUtil.toSystemIndependentName(path))

@ApiStatus.Internal
fun migrateNonJvmSourceFolders(modifiableRootModel: ModifiableRootModel, externalSource: ProjectModelExternalSource) {
    for (contentEntry in modifiableRootModel.contentEntries) {
        for (sourceFolder in contentEntry.sourceFolders) {
            val (newSourceRootType, properties) = sourceFolder.jpsElement.getMigratedSourceRootTypeWithProperties() ?: continue
            val url = sourceFolder.url
            contentEntry.removeSourceFolder(sourceFolder)
            contentEntry.addSourceFolder(url, newSourceRootType, properties, externalSource)
        }
    }
    KotlinSdkType.setUpIfNeeded()
}