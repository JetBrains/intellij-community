// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.configuration

import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProviderTestCase
import org.jetbrains.kotlin.config.VersionView
import org.jetbrains.kotlin.config.apiVersionView
import org.jetbrains.kotlin.config.languageVersionView
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.jvm.shared.framework.JavaFrameworkSupportProvider
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinFrameworkSupportProviderTest : FrameworkSupportProviderTestCase() {
    private fun doTest(provider: FrameworkSupportInModuleProvider) {
        selectFramework(provider).createLibraryDescription()
        addSupport()

        with(KotlinCommonCompilerArgumentsHolder.getInstance(module.project).settings) {
            assertEquals(VersionView.LatestStable, languageVersionView)
            assertEquals(VersionView.LatestStable, apiVersionView)
        }
    }

    fun testJvm() = doTest(JavaFrameworkSupportProvider())
}