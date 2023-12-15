// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.Nls

interface KotlinPluginKindProvider {
    val pluginKind: KotlinPluginKind

    companion object {
        val currentPluginKind: KotlinPluginKind
            get() = ApplicationManager.getApplication().getService(KotlinPluginKindProvider::class.java).pluginKind
    }
}

enum class KotlinPluginKind {
    FE10_PLUGIN {
        override fun other(): KotlinPluginKind = FIR_PLUGIN
    },
    FIR_PLUGIN {
        override fun other(): KotlinPluginKind = FE10_PLUGIN
    };

    abstract fun other(): KotlinPluginKind
}

fun KotlinPluginKind.getPluginKindDescription(): @Nls String {
    return when (this) {
        KotlinPluginKind.FE10_PLUGIN -> KotlinBasePluginBundle.message("kotlin.plugin.kind.k1")
        KotlinPluginKind.FIR_PLUGIN -> KotlinBasePluginBundle.message("kotlin.plugin.kind.k2")
    }
}


fun isK2Plugin(): Boolean {
    return KotlinPluginKindProvider.currentPluginKind == KotlinPluginKind.FIR_PLUGIN
}

/**
 * A switch to mitigate exceptions from Android plugin
 * because it tries to use K1 frontend in K2 plugin.
 *
 * This is a separate method from [isK2Plugin] to better track and update its usages.
 */
fun suppressAndroidPlugin(): Boolean = isK2Plugin()

fun isFe10Plugin(): Boolean {
    return KotlinPluginKindProvider.currentPluginKind == KotlinPluginKind.FE10_PLUGIN
}

fun checkKotlinPluginKind(expectedPluginKind: KotlinPluginKind) {
    val pluginKind = KotlinPluginKindProvider.currentPluginKind
    check(pluginKind == expectedPluginKind) {
        "Invalid Kotlin plugin detected: $pluginKind, but $expectedPluginKind was expected"
    }
}

fun checkKotlinPluginKind(isK2Plugin: Boolean) {
    val pluginKind = if (isK2Plugin) KotlinPluginKind.FIR_PLUGIN else KotlinPluginKind.FE10_PLUGIN
    checkKotlinPluginKind(pluginKind)
}