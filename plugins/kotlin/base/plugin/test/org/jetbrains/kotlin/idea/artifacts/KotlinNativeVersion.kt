/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.artifacts

import org.jetbrains.kotlin.idea.base.plugin.artifacts.kotlincStdlibFileName
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinMavenUtils

object KotlinNativeVersion {
    /** This field is automatically setup from project-module-updater.
     * See [org.jetbrains.tools.model.updater.updateLatestKotlinNativeVersion]
     */
    private const val predefinedKotlinNativeVersion = ""

    val resolvedKotlinNativeVersion: String
        get() {
            return if (predefinedKotlinNativeVersion != "") predefinedKotlinNativeVersion else KotlinMavenUtils.findLibraryVersion(kotlincStdlibFileName)
                ?: error("Can't get '$kotlincStdlibFileName' version")
        }
}