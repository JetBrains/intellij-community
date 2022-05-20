// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinPlatformUtils")
package org.jetbrains.kotlin.idea.base.platforms

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.konan.NativePlatform
import org.jetbrains.kotlin.platform.konan.NativePlatformUnspecifiedTarget
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.UserDataProperty

var KtFile.forcedTargetPlatform: TargetPlatform? by UserDataProperty(Key.create("FORCED_TARGET_PLATFORM"))

@ApiStatus.Internal
fun TargetPlatform.isSharedNative(): Boolean {
    if (this.componentPlatforms.all { it is NativePlatform }) {
        if (this.contains(NativePlatformUnspecifiedTarget)) return true
        return this.componentPlatforms.size > 1
    }
    return false
}