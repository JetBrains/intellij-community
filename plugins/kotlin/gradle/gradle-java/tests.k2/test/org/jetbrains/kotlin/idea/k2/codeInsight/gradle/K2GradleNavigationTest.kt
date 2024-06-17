// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.gradle.AbstractKotlinGradleNavigationTest
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication

private const val SCRIPTING_ENABLED_FLAG = "kotlin.k2.scripting.enabled"

internal fun Disposable.enableK2Scripting() {
    val oldUseScripting = Registry.`is`(SCRIPTING_ENABLED_FLAG, false)
    Registry.get(SCRIPTING_ENABLED_FLAG).setValue(true)
    Disposer.register(this) {
        Registry.get(SCRIPTING_ENABLED_FLAG).setValue(oldUseScripting)
    }
}

@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
class K2GradleNavigationTest : AbstractKotlinGradleNavigationTest() {
    override fun setUp() {
        testRootDisposable.enableK2Scripting()
        super.setUp()
    }
}