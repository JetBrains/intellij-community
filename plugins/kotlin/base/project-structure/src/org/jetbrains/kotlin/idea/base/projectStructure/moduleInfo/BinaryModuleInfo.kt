// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi

@K1ModeProjectStructureApi
interface BinaryModuleInfo : IdeaModuleInfo {
    val sourcesModuleInfo: SourceForBinaryModuleInfo?
}