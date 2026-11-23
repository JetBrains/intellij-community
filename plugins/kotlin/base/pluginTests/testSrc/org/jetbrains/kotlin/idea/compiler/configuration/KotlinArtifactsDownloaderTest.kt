// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.KOTLIN_BUILD_TOOLS_IMPL_ARTIFACT_ID

class KotlinArtifactsDownloaderTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        // This extension point is missing in the test project, so the test registers it
        CoreApplicationEnvironment.registerExtensionPoint(
            project.extensionArea,
            FailedToDownloadJpsMavenArtifactSuggestedSolutionsContributor.EP_NAME,
            FailedToDownloadJpsMavenArtifactSuggestedSolutionsContributor::class.java,
        )
    }

    fun `test the failed download error suggests the remote jar repositories`() {
        val message = KotlinArtifactsDownloader.failedToDownloadUnbundledJpsMavenArtifact(
            project,
            KOTLIN_BUILD_TOOLS_IMPL_ARTIFACT_ID,
            "2.5.20-dev-3865",
        )

        assertTrue(
            "Expected the artifact coordinates in $message",
            "org.jetbrains.kotlin:kotlin-build-tools-impl:2.5.20-dev-3865" in message,
        )
        assertTrue(
            "Expected the Remote Jar Repositories suggestion in $message",
            "- You can check that 'File | Settings | Build, Execution, Deployment | Remote Jar Repositories' " +
                    "has a repository that contains the artifact" in message,
        )
    }
}
