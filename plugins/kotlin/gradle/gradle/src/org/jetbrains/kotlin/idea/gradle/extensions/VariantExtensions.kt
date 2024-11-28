// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.extensions

import org.jetbrains.jps.model.java.JdkVersionDetector.Variant

/**
 * Transform [Variant.name] into expected vendors name by [foojay-plugin](https://github.com/gradle/foojay-toolchains) being the default
 * [plugin resolver](https://docs.gradle.org/current/userguide/toolchain_plugins.html) applied to projects when Gradle
 * toolchains are configured
 */
val Variant.nameSupportedByFoojayPlugin: String?
    get() = when(this) {
        Variant.AdoptOpenJdk_HS -> "AOJ"
        Variant.AdoptOpenJdk_J9 -> "AOJ OpenJ9"
        Variant.JBR -> "JetBrains"
        Variant.IBM -> Variant.Semeru.name
        Variant.Unknown, Variant.GraalVMCE, Variant.Homebrew -> null
        else -> name
    }