// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.run.gradle

import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.TestDisposable
import org.jetbrains.kotlin.idea.k2.codeInsight.gradle.enableK2Scripting
import org.jetbrains.kotlin.idea.run.gradle.AbstractKotlinGradleTaskRunConfigurationProducerTest
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication

@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
class K2GradleTaskRunConfigurationProducerTest : AbstractKotlinGradleTaskRunConfigurationProducerTest(){

    @TestDisposable
    private lateinit var testRootDisposable: Disposable

    override fun setUp() {
        testRootDisposable.enableK2Scripting()
        super.setUp()
    }
}
