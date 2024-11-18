// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.source

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import org.jetbrains.kotlin.idea.base.projectStructure.modules.KaSourceModuleForOutsider
import java.util.*

internal class KaSourceModuleForOutsiderImpl(
    override val entityId: ModuleId,
    override val kind: KaSourceModuleKind,
    override val fakeVirtualFile: VirtualFile,
    override val originalVirtualFile: VirtualFile?,
    override val project: Project,
) : KaSourceModuleBase(), KaSourceModuleForOutsider {
    override val contentScope: GlobalSearchScope
        get() = adjustContentScope(super.contentScope)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is KaSourceModuleForOutsiderImpl
                && fakeVirtualFile == other.fakeVirtualFile
                && entityId == other.entityId
                && kind == other.kind
    }

    override fun hashCode(): Int {
        return Objects.hash(fakeVirtualFile, entityId, kind)
    }

    override fun toString(): String {
        return super.toString() + ", fakeVirtualFile=$fakeVirtualFile, originalVirtualFile=$originalVirtualFile"
    }
}