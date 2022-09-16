// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryWrapper
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.TargetPlatform

class CommonMetadataLibraryInfo(project: Project, libraryWrapper: LibraryWrapper) : LibraryInfo(project, libraryWrapper) {
    override val platform: TargetPlatform
        get() = CommonPlatforms.defaultCommonPlatform
}
