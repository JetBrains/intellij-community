// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.plugin.useK2Plugin
import org.junit.Assert

interface ExpectedPluginModeProvider {

    val pluginMode: KotlinPluginMode
}

fun ExpectedPluginModeProvider.assertKotlinPluginMode() {
    val expectedPluginMode = pluginMode
    val actualPluginMode = KotlinPluginModeProvider.currentPluginMode

    Assert.assertEquals(
        "Invalid Kotlin plugin detected: $actualPluginMode, but $expectedPluginMode was expected",
        expectedPluginMode,
        actualPluginMode,
    )
}

/**
 * Executes a [setUp] function after enabling the K1 or K2 Kotlin plugin in system properties.
 * The correct Kotlin plugin should be set up after [setUp] finishes.
 */
fun ExpectedPluginModeProvider.setUpWithKotlinPlugin(setUp: () -> Unit) {
    useK2Plugin = pluginMode == KotlinPluginMode.K2
    setUp()
    assertKotlinPluginMode()
}
