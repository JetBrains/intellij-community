// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin

import com.intellij.openapi.components.service
import org.jetbrains.annotations.Nls

interface KotlinPluginModeProvider {

    val pluginMode: KotlinPluginMode

    companion object {

        val currentPluginMode: KotlinPluginMode
            get() = service<KotlinPluginModeProvider>().pluginMode

        fun isK2Mode(): Boolean =
            currentPluginMode == KotlinPluginMode.K2

        fun isK1Mode(): Boolean =
            currentPluginMode == KotlinPluginMode.K1
    }
}

enum class KotlinPluginMode(
    private val propertyKey: String // TODO @PropertyKey,
) {

    K1(propertyKey = "kotlin.plugin.kind.k1") {

        override val other: KotlinPluginMode get() = K2
    },
    K2(propertyKey = "kotlin.plugin.kind.k2") {

        override val other: KotlinPluginMode get() = K1
    };

    abstract val other: KotlinPluginMode

    val pluginModeDescription: @Nls String
        get() = KotlinBasePluginBundle.message(propertyKey)

    companion object {

        @JvmStatic
        fun of(useK2Plugin: Boolean): KotlinPluginMode =
            if (useK2Plugin) K2 else K1
    }
}
