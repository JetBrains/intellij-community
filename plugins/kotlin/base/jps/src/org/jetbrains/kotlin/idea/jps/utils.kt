// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jps

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import java.io.File

const val KOTLIN_BUNDLED: @NonNls String = "KOTLIN_BUNDLED"

fun File.toJpsVersionAgnosticKotlinBundledPath(): String {
    val kotlincDirectory = KotlinPluginLayout.kotlinc
    require(this.startsWith(kotlincDirectory)) { "$this should start with ${kotlincDirectory}" }
    return "\$$KOTLIN_BUNDLED\$/${this.relativeTo(kotlincDirectory)}"
}