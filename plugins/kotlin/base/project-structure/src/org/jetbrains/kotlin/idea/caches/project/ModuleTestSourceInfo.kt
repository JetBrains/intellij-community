// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus

@Deprecated("Use 'org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleTestSourceInfo' instead")
@ApiStatus.ScheduledForRemoval
interface ModuleTestSourceInfo {
    val module: Module
}