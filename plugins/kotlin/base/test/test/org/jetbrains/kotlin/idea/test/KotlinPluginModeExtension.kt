// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test

import com.intellij.util.ThreeState
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.useK2Plugin
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext

@TestOnly
sealed class KotlinPluginModeExtension(
    private val pluginMode: KotlinPluginMode,
) : BeforeAllCallback,
    AfterAllCallback {

    class K1 : KotlinPluginModeExtension(KotlinPluginMode.K1)
    class K2 : KotlinPluginModeExtension(KotlinPluginMode.K2)

    private lateinit var oldUseK2Plugin: ThreeState

    override fun beforeAll(context: ExtensionContext) {
        oldUseK2Plugin = when (useK2Plugin) {
            null -> ThreeState.UNSURE
            false -> ThreeState.NO
            true -> ThreeState.YES
        }

        useK2Plugin = pluginMode == KotlinPluginMode.K2
    }

    override fun afterAll(context: ExtensionContext) {
        useK2Plugin = when (oldUseK2Plugin) {
            ThreeState.YES -> true
            ThreeState.NO -> false
            ThreeState.UNSURE -> null
        }
    }
}

@TestOnly
@Target(AnnotationTarget.CLASS)
@ExtendWith(KotlinPluginModeExtension.K1::class)
annotation class UseK1PluginMode

@TestOnly
@Target(AnnotationTarget.CLASS)
@ExtendWith(KotlinPluginModeExtension.K2::class)
annotation class UseK2PluginMode

@TestOnly
class AssertKotlinPluginModeExtension : BeforeAllCallback {

    override fun beforeAll(context: ExtensionContext) {
        assertKotlinPluginMode(
            expectedPluginMode = useK2Plugin?.let { KotlinPluginMode.of(it) } ?: KotlinPluginMode.K2,
        )
    }
}

@TestOnly
@Target(AnnotationTarget.CLASS)
@ExtendWith(AssertKotlinPluginModeExtension::class)
annotation class AssertKotlinPluginMode