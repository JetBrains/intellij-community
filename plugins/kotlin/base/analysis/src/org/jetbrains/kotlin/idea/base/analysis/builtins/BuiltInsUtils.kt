// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.builtins

import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.platform.isWasm
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.isNative

/**
 * Checks if a source module with the [this] target will depend on a common stdlib artifact.
 *
 * This also means that the module is `common` in HMPP terms
 */
fun TargetPlatform.hasCommonKotlinStdlib(): Boolean {
    if (componentPlatforms.size <= 1) return false
    if (isJvm()) return false
    if (isJs()) return false
    if (isWasm()) return false
    if (isNative()) return false
    return true
}