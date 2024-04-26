// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.Nls

interface KotlinPluginModeProvider {
    val pluginMode: KotlinPluginMode

    companion object {
        val currentPluginMode: KotlinPluginMode
            get() = ApplicationManager.getApplication().getService(KotlinPluginModeProvider::class.java).pluginMode

        fun isK2Mode(): Boolean {
            return currentPluginMode == KotlinPluginMode.K2
        }

        fun isK1Mode(): Boolean {
            return currentPluginMode == KotlinPluginMode.K1
        }
    }
}

enum class KotlinPluginMode {
    K1 {
        override fun other(): KotlinPluginMode = K2
    },
    K2 {
        override fun other(): KotlinPluginMode = K1
    };

    abstract fun other(): KotlinPluginMode
}

fun KotlinPluginMode.getPluginModeDescription(): @Nls String {
    return when (this) {
        KotlinPluginMode.K1 -> KotlinBasePluginBundle.message("kotlin.plugin.kind.k1")
        KotlinPluginMode.K2 -> KotlinBasePluginBundle.message("kotlin.plugin.kind.k2")
    }
}


@Deprecated(
    "Use `KotlinPluginModeProvider.isK2Mode()` instead",
    replaceWith = ReplaceWith("KotlinPluginModeProvider.isK2Mode()", "org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider")
)
fun isK2Plugin(): Boolean {
    return KotlinPluginModeProvider.currentPluginMode == KotlinPluginMode.K2
}


/**
 * A switch to mitigate exceptions from Android plugin
 * because it tries to use K1 frontend in K2 plugin.
 *
 * This is a separate method from [KotlinPluginModeProvider.isK2Mode] to better track and update its usages.
 */
fun suppressAndroidPlugin(): Boolean = KotlinPluginModeProvider.isK2Mode()

fun checkKotlinPluginMode(expectedPluginKind: KotlinPluginMode) {
    val pluginKind = KotlinPluginModeProvider.currentPluginMode
    check(pluginKind == expectedPluginKind) {
        "Invalid Kotlin plugin detected: $pluginKind, but $expectedPluginKind was expected"
    }
}

fun checkKotlinPluginMode(isK2Plugin: Boolean) {
    val pluginKind = if (isK2Plugin) KotlinPluginMode.K2 else KotlinPluginMode.K1
    checkKotlinPluginMode(pluginKind)
}