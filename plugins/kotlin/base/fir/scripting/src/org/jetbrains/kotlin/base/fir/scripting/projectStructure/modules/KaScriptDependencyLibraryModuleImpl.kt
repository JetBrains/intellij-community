// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.base.fir.scripting.projectStructure.modules

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.workspaceModel.ide.toPath
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaScriptDependencyModule
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library.KaEntityBasedLibraryModuleBase
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path
import java.util.Objects

class KaScriptDependencyLibraryModuleImpl(
    override val entity: KotlinScriptLibraryEntity,
    override val project: Project,
) : KaEntityBasedLibraryModuleBase<KotlinScriptLibraryEntity, KotlinScriptLibraryEntityId>(), KaScriptDependencyModule {
    override val file: KtFile? get() = null

    override val entityId: KotlinScriptLibraryEntityId
        get() = entity.symbolicId

    @KaPlatformInterface
    override val isSdk: Boolean = false

    override val targetPlatform: TargetPlatform
        get() = JvmPlatforms.unspecifiedJvmPlatform

    override val libraryName: String
        get() = entity.classes.singleOrNull()?.presentableUrl ?: entity.symbolicId.presentableName

    @KaExperimentalApi
    override val binaryVirtualFiles: Collection<VirtualFile>
        get() = entity.classes.mapNotNull { it.virtualFile }

    override val binaryRoots: Collection<Path>
        get() = entity.classes.map { it.toPath() }

    override val librarySources: KaLibrarySourceModule? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        KaScriptDependencyLibrarySourceModuleImpl(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KaScriptDependencyLibraryModuleImpl

        if (entity.symbolicId != other.entity.symbolicId) return false
        if (project != other.project) return false

        return true
    }

    override fun hashCode(): Int = Objects.hash(entity.symbolicId, project)
}
