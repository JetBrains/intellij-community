// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin

import com.intellij.openapi.application.ApplicationManager

abstract class KotlinPluginKindProvider {
    abstract fun getPluginKind(): KotlinPluginKind
}

fun checkKotlinPluginKind(expectedPluginKind: KotlinPluginKind) {
    val plugin = ApplicationManager.getApplication().getService(KotlinPluginKindProvider::class.java).getPluginKind()
    check (plugin == expectedPluginKind) {
        "Invalid Kotlin plugin detected: $plugin, but $expectedPluginKind was expected"
    }
}

enum class KotlinPluginKind {
    FE10_PLUGIN,
    FIR_PLUGIN
}