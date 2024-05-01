// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.highlighting

import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.codeInsight.gradle.AbstractGradleBuildFileHighlightingTest
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.assertKotlinPluginMode


private const val SCRIPTING_ENABLED_FLAG = "kotlin.k2.scripting.enabled"

abstract class AbstractK2GradleBuildFileHighlightingTest : AbstractGradleBuildFileHighlightingTest(),
                                                           ExpectedPluginModeProvider {

    private var originalRegistryFlag: String? = null

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun setUp() {
        originalRegistryFlag = Registry.getInstance().getBundleValueOrNull(SCRIPTING_ENABLED_FLAG)

        Registry.get(SCRIPTING_ENABLED_FLAG).setValue(true)

        super.setUp()
        assertKotlinPluginMode()
    }

    override fun tearDown() {
        runAll(
            { originalRegistryFlag?.let { Registry.get(SCRIPTING_ENABLED_FLAG).setValue(it) } },
            { super.tearDown() }
        )
    }

    override val outputFileExt: String = ".highlighting.k2"
}