// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jps

import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import java.nio.file.Path

fun Path.toJpsVersionAgnosticKotlinBundledPath(): String {
    val kotlincDirectory = KotlinPluginLayout.kotlincPath
    require(this.startsWith(kotlincDirectory)) { "$this should start with ${kotlincDirectory}" }
    return "\$KOTLIN_BUNDLED\$/${relativize(kotlincDirectory)}"
}