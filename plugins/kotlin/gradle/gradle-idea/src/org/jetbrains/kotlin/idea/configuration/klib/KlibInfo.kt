/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.configuration.klib

import java.io.File

internal data class KlibInfo(
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




