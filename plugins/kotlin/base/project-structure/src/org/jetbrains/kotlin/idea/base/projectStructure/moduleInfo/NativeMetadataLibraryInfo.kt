// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.konan.NativePlatforms

@K1ModeProjectStructureApi
class NativeMetadataLibraryInfo internal constructor(project: Project, library: LibraryEx) : LibraryInfo(project, library) {
    override val platform: TargetPlatform get() = NativePlatforms.unspecifiedNativePlatform
}