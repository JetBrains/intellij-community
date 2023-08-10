// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin

import org.jetbrains.kotlin.idea.base.analysis.libraries.DefaultLibraryDependencyCandidate
import org.jetbrains.kotlin.idea.base.analysis.libraries.KlibLibraryDependencyCandidate
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.platform.SimplePlatform
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.konan.NativePlatformWithTarget

data class TestPlatform(val name: String) : SimplePlatform(name) {
    override val oldFashionedDescription = name
    override fun toString(): String {
        return name
    }
}

fun platform(vararg name: String) = TargetPlatform(name.map(::TestPlatform).toSet())

fun platform(vararg simplePlatform: SimplePlatform) = TargetPlatform(setOf(*simplePlatform))

fun platform(vararg konanTarget: KonanTarget) = TargetPlatform(konanTarget.map(::NativePlatformWithTarget).toSet())

internal fun libraryDependencyCandidate(platform: TargetPlatform): DefaultLibraryDependencyCandidate =
    DefaultLibraryDependencyCandidate(platform, emptyList())

internal fun klibLibraryDependencyCandidate(platform: TargetPlatform, uniqueName: String, isInterop: Boolean):
        KlibLibraryDependencyCandidate = KlibLibraryDependencyCandidate(platform, emptyList(), uniqueName, isInterop)
