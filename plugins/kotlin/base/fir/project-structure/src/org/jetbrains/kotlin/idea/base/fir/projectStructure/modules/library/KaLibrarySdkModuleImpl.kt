// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.KaEntityBasedModuleCreationData
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.KaModuleWithDebugData
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.librarySource.KaLibrarySdkSourceModuleImpl
import org.jetbrains.kotlin.idea.base.fir.projectStructure.provider.InternalKaModuleConstructor
import org.jetbrains.kotlin.idea.base.util.caching.findSdkBridge
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

internal class KaLibrarySdkModuleImpl @InternalKaModuleConstructor constructor(
    override val project: Project,
    override val entityId: SdkId,
    override val creationData: KaEntityBasedModuleCreationData
) : KaLibraryModuleBase<SdkEntity, SdkId>(), KaModuleWithDebugData {

    val sdk: Sdk
        get() = entity.findSdkBridge(currentSnapshot)
            ?: error("Could not find SDK $entityId")

    @KaExperimentalApi
    override val binaryVirtualFiles: Collection<VirtualFile> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        sdk.rootProvider.getFiles(OrderRootType.CLASSES).toList()
    }

    override val libraryName: String get() = entity.name

    override val librarySources: KaLibrarySourceModule? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        KaLibrarySdkSourceModuleImpl(this)
    }

    @KaPlatformInterface
    override val isSdk: Boolean get() = true

    override val targetPlatform: TargetPlatform
        get() = when (sdk.sdkType) {
            is KotlinSdkType -> CommonPlatforms.defaultCommonPlatform
            else -> JvmPlatforms.unspecifiedJvmPlatform
        }

    override val entityInterface: Class<out WorkspaceEntity> get() = SdkEntity::class.java

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is KaLibrarySdkModuleImpl
                && other.entity == entityId
    }

    override fun hashCode(): Int {
        return entityId.hashCode()
    }
}