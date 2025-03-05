// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.source

import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.ModuleId
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import java.util.*

internal class KaSourceModuleImpl(
    override val entityId: ModuleId,
    override val kind: KaSourceModuleKind,
    override val project: Project,
) : KaSourceModuleBase() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is KaSourceModuleImpl
                && entityId == other.entityId
                && kind == other.kind
    }

    override fun hashCode(): Int {
        return Objects.hash(entityId, kind)
    }
}