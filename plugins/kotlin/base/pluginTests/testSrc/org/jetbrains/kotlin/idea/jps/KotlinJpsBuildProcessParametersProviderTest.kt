// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jps

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettingsTestUtil

class KotlinJpsBuildProcessParametersProviderTest : BasePlatformTestCase() {
    fun `test an unsupported Kotlin version fails the classpath with the rejection message`() {
        assertProviderFailsWithRejectionMessage { classPath }
    }

    fun `test an unsupported Kotlin version fails the path parameters with the rejection message`() {
        assertProviderFailsWithRejectionMessage { pathParameters }
    }

    private fun assertProviderFailsWithRejectionMessage(parameters: KotlinJpsBuildProcessParametersProvider.() -> Unit) {
        KotlinJpsPluginSettingsTestUtil.setBuildToolsApiEnabled(false, testRootDisposable)
        KotlinJpsPluginSettingsTestUtil.setProjectJpsVersion(project, "2.6.0", testRootDisposable)

        try {
            parameters(KotlinJpsBuildProcessParametersProvider(project))
            fail("Expected the provider to fail for Kotlin 2.6.0 with the Build Tools API off")
        } catch (e: IllegalStateException) {
            assertEquals(KotlinJpsPluginSettingsTestUtil.legacyCompilerRejectionMessage("2.6.0"), e.message)
        }
    }
}
