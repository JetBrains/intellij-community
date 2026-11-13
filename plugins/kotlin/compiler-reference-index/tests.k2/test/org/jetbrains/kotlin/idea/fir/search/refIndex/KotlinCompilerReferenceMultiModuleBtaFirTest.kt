// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.search.refIndex

import com.intellij.jarRepository.RemoteRepositoriesConfiguration
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.SkipSlowTestLocally
import org.jetbrains.kotlin.idea.artifacts.KotlinJpsBuilderTestArtifacts
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerWorkspaceSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.search.refIndex.AbstractKotlinCompilerReferenceMultiModuleTest
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorageProvider
import org.jetbrains.kotlin.idea.search.refIndex.jps.isJpsBtaCriProvider
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import kotlin.io.path.isDirectory

/**
 * Runs the multi-module compiler reference index tests on JPS builds that compile through the Build Tools API.
 *
 * The registry key turns the runner on. The build uses the in-repo JPS builder, and the JPS BTA provider reads
 * the index from each module target's data root.
 */
@SkipSlowTestLocally
class KotlinCompilerReferenceMultiModuleBtaFirTest : AbstractKotlinCompilerReferenceMultiModuleTest() {

    override fun setUp() {
        super.setUp()
        Registry.get("kotlin.jps.use.build.tools.api").setValue(true, testRootDisposable)
        // The daemon execution policy compiles through the daemon, which writes no compiler reference index
        KotlinCompilerWorkspaceSettings.getInstance(project).enableDaemon = false
        ApplicationManager.getApplication().invokeAndWait {
            runWriteAction { KotlinJpsPluginSettings.getInstance(project).setVersion(PINNED_KOTLIN_VERSION) }
        }
        setUpExperimentalKotlinRepository()
        ensureJpsPluginDependencies()
    }

    override fun rebuildProject() {
        super.rebuildProject()
        // The old JPS provider passes the inherited assertions too, so pin the provider that served the build
        assertTrue(
            "Expected the JPS BTA provider after a BTA build",
            KotlinCompilerReferenceIndexStorageProvider.getApplicableProvider(project).isJpsBtaCriProvider()
        )
    }

    fun `test a usage comes from the JPS cri data`() {
        val utilFile = myFixture.addFileToProject("util.kt", "package one\nopen class Util")
        myFixture.addFileToProject("usage.kt", "package two\nimport one.Util\nfun usage() = Util()")
        myFixture.addFileToProject("unrelated.kt", "package two\nfun unrelated() = 1")

        installCompiler()
        rebuildProject()

        val utilClass = (utilFile as KtFile).declarations.filterIsInstance<KtClass>().single()
        val usageFileNames = getReferentFiles(utilClass, false) ?: error("no usages found for one.Util")
        assertTrue("Expected usage.kt among $usageFileNames", "usage.kt" in usageFileNames)
        assertFalse("Expected no unrelated.kt among $usageFileNames", "unrelated.kt" in usageFileNames)
    }

    private fun setUpExperimentalKotlinRepository() {
        // Kotlin dev builds are not on Maven Central, and the lazy download reads the project's repository list
        val configuration = RemoteRepositoriesConfiguration.getInstance(project)
        if (configuration.repositories.none { it.url == KOTLIN_EXPERIMENTAL_REPOSITORY_URL }) {
            configuration.setRepositories(
                configuration.repositories + RemoteRepositoryDescription(
                    "kotlin-experimental",
                    "Kotlin experimental repository",
                    KOTLIN_EXPERIMENTAL_REPOSITORY_URL
                )
            )
        }
    }

    private fun ensureJpsPluginDependencies() {
        // The artifact resolution must not run on the EDT
        ApplicationManager.getApplication().executeOnPooledThread<Unit> {
            val prepared = KotlinArtifactsDownloader.lazyDownloadMissingJpsPluginDependencies(
                project = project,
                jpsVersion = PINNED_KOTLIN_VERSION,
                indicator = EmptyProgressIndicator(),
                onError = { fail("Cannot prepare Kotlin $PINNED_KOTLIN_VERSION for the test: $it") }
            )
            assertTrue("Cannot prepare Kotlin $PINNED_KOTLIN_VERSION for the test", prepared)
            // The version gate treats the implementation as optional, but the BTA runner requires it
            assertTrue(
                "The Build Tools API implementation for Kotlin $PINNED_KOTLIN_VERSION is missing",
                KotlinArtifactsDownloader.getBuildToolsImplDirectory(PINNED_KOTLIN_VERSION).isDirectory()
            )
        }.get()
    }

    companion object {
        // TODO Pin a released Kotlin 2.5.20 once it's released, because the dev build resolves only from the experimental repository
        // The bundled Kotlin 2.4.21 predates the Build Tools API implementation, so the tests cannot run on it. The
        // version follows the implementation of the builder tests, so both compile with the same one.
        private val PINNED_KOTLIN_VERSION = KotlinJpsBuilderTestArtifacts.BUILD_TOOLS_IMPL_BOOTSTRAP_VERSION
        private const val KOTLIN_EXPERIMENTAL_REPOSITORY_URL = "https://packages.jetbrains.team/maven/p/kt/experimental"
    }
}
