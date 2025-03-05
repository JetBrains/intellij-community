// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.PlatformModuleInfo

@ApiStatus.ScheduledForRemoval
@Deprecated("Use 'org.jetbrains.kotlin.idea.base.projectStructure.unwrapModuleSourceInfo()' instead")
@Suppress("unused", "DEPRECATION", "DeprecatedCallableAddReplaceWith")
fun ModuleInfo.unwrapModuleSourceInfo(): org.jetbrains.kotlin.idea.caches.project.ModuleSourceInfo? {
    return when (this) {
        is ModuleSourceInfo -> this
        is PlatformModuleInfo -> this.platformModule
        else -> null
    }
}