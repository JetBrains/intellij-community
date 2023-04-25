// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin

import com.intellij.openapi.application.ApplicationManager

interface KotlinPluginKindProvider {
    val pluginKind: KotlinPluginKind
}

enum class KotlinPluginKind {
    FE10_PLUGIN,
    FIR_PLUGIN
}

private val currentPluginKind: KotlinPluginKind
    get() = ApplicationManager.getApplication().getService(KotlinPluginKindProvider::class.java).pluginKind

fun isK2Plugin(): Boolean {
    return currentPluginKind == KotlinPluginKind.FIR_PLUGIN
}

/**
 * A switch to mitigate exceptions from Android plugin
 * because it tries to use K1 frontend in K2 plugin.
 *
 * This is a separate method from [isK2Plugin] to better track and update its usages.
 */
fun suppressAndroidPlugin(): Boolean = isK2Plugin()

fun isFe10Plugin(): Boolean {
    return currentPluginKind == KotlinPluginKind.FE10_PLUGIN
}

fun checkKotlinPluginKind(expectedPluginKind: KotlinPluginKind) {
    val pluginKind = currentPluginKind
    check(pluginKind == expectedPluginKind) {
        "Invalid Kotlin plugin detected: $pluginKind, but $expectedPluginKind was expected"
    }
}