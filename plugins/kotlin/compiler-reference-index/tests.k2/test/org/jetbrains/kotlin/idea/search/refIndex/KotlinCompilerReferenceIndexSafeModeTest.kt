// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.backwardRefs.IsUpToDateCheckConsumer
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.OpenUntrustedProjectChoice
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.impl.TrustedProjectStartupDialog
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.useProjectAsync
import com.intellij.util.ThreeState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
@SystemProperty("idea.trust.headless.disabled", "false")
internal class KotlinCompilerReferenceIndexSafeModeTest {
    private val projectRoot by tempPathFixture()

    @TestDisposable
    private lateinit var disposable: Disposable

    @Test
    fun `does not inspect KCRI state in Safe Mode`() = timeoutRunBlocking {
        val provider = registerProvider()

        openProject("safe-mode", OpenUntrustedProjectChoice.OPEN_IN_SAFE_MODE).useProjectAsync { project ->
            assertEquals(ThreeState.NO, TrustedProjects.getProjectTrustedState(project))
            assertFalse(kcriConsumer().isApplicable(project))
            assertEquals(0, provider.hasIndexCalls)
        }
    }

    @Test
    fun `inspects KCRI state for a trusted project`() = timeoutRunBlocking {
        val provider = registerProvider()

        openProject("trusted", OpenUntrustedProjectChoice.TRUST_AND_OPEN).useProjectAsync { project ->
            assertTrue(TrustedProjects.isProjectTrusted(project))
            assertTrue(kcriConsumer().isApplicable(project))
            assertEquals(1, provider.hasIndexCalls)
        }
    }

    private fun registerProvider(): RecordingStorageProvider = RecordingStorageProvider().also {
        KotlinCompilerReferenceIndexStorageProvider.EP_NAME.point.registerExtension(it, disposable)
    }

    // the consumer is internal, locate the extension by its declaring class
    private fun kcriConsumer(): IsUpToDateCheckConsumer =
        IsUpToDateCheckConsumer.EP_NAME.extensionList.single {
            it.javaClass.declaringClass == KotlinCompilerReferenceIndexService::class.java
        }

    private suspend fun openProject(name: String, openChoice: OpenUntrustedProjectChoice): Project {
        TrustedProjectStartupDialog.setDialogChoiceInTests(openChoice, disposable)
        return ProjectManagerEx.getInstanceEx()
            .openProjectAsync(projectRoot.resolve(name), OpenProjectTask { projectName = name })!!
    }

    private class RecordingStorageProvider : KotlinCompilerReferenceIndexStorageProvider {
        var hasIndexCalls = 0

        override fun isApplicable(project: Project): Boolean = true

        override fun hasIndex(project: Project): Boolean {
            hasIndexCalls++
            return true
        }

        override fun createStorage(project: Project, projectPath: String): KotlinCompilerReferenceIndexStorage? = null
    }
}
