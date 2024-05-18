// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.ThrowableRunnable
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.plugin.useK2Plugin
import org.junit.Assert

@TestOnly
interface ExpectedPluginModeProvider {

    val pluginMode: KotlinPluginMode
}

// do not expose
private fun ExpectedPluginModeProvider.assertKotlinPluginMode() {
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
@TestOnly
fun ExpectedPluginModeProvider.setUpWithKotlinPlugin(
    rootDisposable: Disposable,
    setUp: ThrowableRunnable<*>,
) {
    val oldUseK2Plugin = useK2Plugin
    useK2Plugin = pluginMode == KotlinPluginMode.K2
    Disposer.register(rootDisposable) {
        useK2Plugin = oldUseK2Plugin
    }

    setUp.run()

    assertKotlinPluginMode()
}

@TestOnly
fun <T> T.setUpWithKotlinPlugin(
    setUp: ThrowableRunnable<*>,
) where T : UsefulTestCase,
        T : ExpectedPluginModeProvider =
    setUpWithKotlinPlugin(testRootDisposable, setUp)

