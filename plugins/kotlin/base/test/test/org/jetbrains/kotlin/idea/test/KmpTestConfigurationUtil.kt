// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.USE_K2_PLUGIN_PROPERTY_NAME
import org.jetbrains.kotlin.idea.base.plugin.useK2Plugin

@TestOnly
fun <T> T.enableKmpWasmSupport() where T : UsefulTestCase, T : ExpectedPluginModeProvider {
    if (this.pluginMode == KotlinPluginMode.K2) {
        check(useK2Plugin == true) { "Expected '$USE_K2_PLUGIN_PROPERTY_NAME' to be set, please set up the plugin before enabling KMP" }
        Registry.get("kotlin.k2.kmp.wasm.enabled").setValue(true, testRootDisposable)
    }
}
