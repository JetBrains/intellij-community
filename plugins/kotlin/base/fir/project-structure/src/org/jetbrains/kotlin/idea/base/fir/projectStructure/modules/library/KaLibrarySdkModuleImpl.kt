// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.customName
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.KaEntityBasedModuleCreationData
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.KaModuleWithDebugData
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.librarySource.KaLibrarySdkSourceModuleImpl
import org.jetbrains.kotlin.idea.base.fir.projectStructure.provider.InternalKaModuleConstructor
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

internal class KaLibrarySdkModuleImpl @InternalKaModuleConstructor constructor(
    override val project: Project,
    override val entityId: SdkId,
    override val creationData: KaEntityBasedModuleCreationData
) : KaEntityBasedLibraryModuleBase<SdkEntity, SdkId>(), KaModuleWithDebugData {

    @KaExperimentalApi
    override val binaryVirtualFiles: Collection<VirtualFile> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        computeRoots(OrderRootType.CLASSES)
    }

    override val libraryName: String get() = entity.name

    override val librarySources: KaLibrarySourceModule? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        KaLibrarySdkSourceModuleImpl(this)
    }

    @KaPlatformInterface
    override val isSdk: Boolean get() = true

    override val targetPlatform: TargetPlatform
        get() = when (entityId.type) {
            KotlinSdkType.NAME -> CommonPlatforms.defaultCommonPlatform
            else -> JvmPlatforms.unspecifiedJvmPlatform
        }

    override val entityInterface: Class<out WorkspaceEntity> get() = SdkEntity::class.java

    internal fun computeRoots(orderRootType: OrderRootType): List<VirtualFile> {
        return entity.roots
            .filter { it.type.name == orderRootType.customName }
            .mapNotNull { it.url.virtualFile }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is KaLibrarySdkModuleImpl
                && other.entityId == entityId
    }

    override fun hashCode(): Int {
        return entityId.hashCode()
    }
}