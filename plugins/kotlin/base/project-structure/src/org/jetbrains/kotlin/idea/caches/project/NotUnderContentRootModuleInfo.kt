// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.project.ProjectManager
import org.jetbrains.kotlin.analyzer.NonSourceModuleInfoBase
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.NotUnderContentRootModuleInfo as NewNotUnderContentRootModuleInfo

@Deprecated("Use 'org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.NotUnderContentRootModuleInfo' instead.")
abstract class NotUnderContentRootModuleInfo : IdeaModuleInfo, NonSourceModuleInfoBase {
    companion object {
        @JvmField
        val INSTANCE: NotUnderContentRootModuleInfo = NewNotUnderContentRootModuleInfo(ProjectManager.getInstance().defaultProject, null)
    }
}
