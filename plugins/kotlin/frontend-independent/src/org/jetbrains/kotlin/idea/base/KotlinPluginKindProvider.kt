// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base

import com.intellij.openapi.application.ApplicationManager

abstract class KotlinPluginKindProvider {
    abstract fun getPluginKind(): KotlinPluginKind
}

fun assertKotlinPluginKind(expectedPlugin: KotlinPluginKind) {
    val plugin = ApplicationManager.getApplication().getService(KotlinPluginKindProvider::class.java).getPluginKind()
    check (plugin == expectedPlugin) {
        "Invalid Kotlin plugin detected: $plugin, but $expectedPlugin was expected"
    }
}

enum class KotlinPluginKind {
    FE10_PLUGIN,
    FIR_PLUGIN
}