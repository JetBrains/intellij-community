// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

import java.io.File
import java.io.Serializable

interface KotlinLanguageSettings : Serializable {
    val languageVersion: String?
    val apiVersion: String?
    val isProgressiveMode: Boolean
    val enabledLanguageFeatures: Set<String>

    @Deprecated("Unsupported and will be removed in next major releases", replaceWith = ReplaceWith("optInAnnotationsInUse"))
    val experimentalAnnotationsInUse: Set<String>
        get() = optInAnnotationsInUse
    val optInAnnotationsInUse: Set<String>
    val compilerPluginArguments: Array<String>
    val compilerPluginClasspath: Set<File>
    val freeCompilerArgs: Array<String>
}
