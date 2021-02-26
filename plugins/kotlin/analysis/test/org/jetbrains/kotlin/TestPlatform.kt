package org.jetbrains.kotlin

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

fun platform(vararg simplePlatform: SimplePlatform) = TargetPlatform(setOf(*simplePlatform))

fun platform(vararg konanTarget: KonanTarget) = TargetPlatform(konanTarget.map(::NativePlatformWithTarget).toSet())

