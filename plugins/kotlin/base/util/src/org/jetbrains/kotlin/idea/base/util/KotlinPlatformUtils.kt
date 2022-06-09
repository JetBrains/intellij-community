// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.util

import com.intellij.util.PlatformUtils

object KotlinPlatformUtils {
    @JvmStatic
    val isAndroidStudio: Boolean = PlatformUtils.getPlatformPrefix() == "AndroidStudio"

    @JvmStatic
    val isCidr: Boolean = PlatformUtils.isCidr()
}