// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinPlatformUtils")
package org.jetbrains.kotlin.idea.base.platforms

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.JsPlatform
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.has
import org.jetbrains.kotlin.platform.impl.CommonIdePlatformKind
import org.jetbrains.kotlin.platform.impl.JsIdePlatformKind
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind
import org.jetbrains.kotlin.platform.impl.NativeIdePlatformKind
import org.jetbrains.kotlin.platform.jvm.JvmPlatform
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

fun IdePlatformKind.isCompatibleWith(platform: TargetPlatform): Boolean {
    return when (this) {
        is JvmIdePlatformKind -> platform.has(JvmPlatform::class)
        is NativeIdePlatformKind -> platform.has(NativePlatform::class)
        is JsIdePlatformKind -> platform.has(JsPlatform::class)
        is CommonIdePlatformKind -> true
        else -> false
    }
}