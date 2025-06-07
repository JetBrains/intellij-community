// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.base.fir.scripting.projectStructure.modules

import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.LibraryId
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaScriptDependencyModule
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library.KaLibraryEntityBasedLibraryModuleBase
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile

internal class KaScriptDependencyLibraryModuleImpl(
    override val entityId: LibraryId,
    override val project: Project,
) : KaLibraryEntityBasedLibraryModuleBase(), KaScriptDependencyModule {
    override val file: KtFile? get() = null

    override val librarySources: KaLibrarySourceModule? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        KaScriptDependencyLibrarySourceModuleImpl(this)
    }

    override val targetPlatform: TargetPlatform
        get() = JvmPlatforms.unspecifiedJvmPlatform

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is KaScriptDependencyLibraryModuleImpl
                && entityId == other.entityId
    }

    override fun hashCode(): Int {
        return entityId.hashCode()
    }
}
