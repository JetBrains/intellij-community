// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradle.configuration.klib

import com.intellij.openapi.util.IntellijInternalApi
import java.io.File

@IntellijInternalApi
data class KlibInfo(
    val path: File,
    val sourcePaths: Collection<File>,
    val libraryName: String,
    val isStdlib: Boolean,
    val isCommonized: Boolean,
    val isFromNativeDistribution: Boolean,
    val targets: NativeTargets?
) {

    sealed class NativeTargets {
        final override fun toString(): String = when (this) {
            is CommonizerIdentity -> identityString
            is NativeTargetsList -> nativeTargets
        }

        data class CommonizerIdentity(val identityString: String) : NativeTargets()
        data class NativeTargetsList(val nativeTargets: String) : NativeTargets()
    }
}




