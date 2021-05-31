// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.replaceService
import junit.framework.AssertionFailedError
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.applySuggestedScriptConfiguration
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.loader.DefaultScriptConfigurationLoader
import org.jetbrains.kotlin.idea.core.script.configuration.loader.ScriptConfigurationLoadingContext
import org.jetbrains.kotlin.idea.core.script.configuration.utils.areSimilar
import org.jetbrains.kotlin.idea.core.script.configuration.utils.getKtFile
import org.jetbrains.kotlin.idea.scripting.gradle.importing.KotlinGradleDslErrorReporter.Companion.build_script_errors_group
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.plugins.gradle.service.project.ProjectModelContributor
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.File

@RunWith(value = Parameterized::class)
class GradleKtsImportTest : KotlinGradleImportingTestCase() {
    companion object {
        @JvmStatic
        @Suppress("ACCIDENTAL_OVERRIDE")
        @Parameters(name = "{index}: with Gradle-{0}")
        fun data(): Collection<Array<Any?>> = listOf(arrayOf("6.0.1"))
    }

    val projectDir: File
        get() = File(GradleSettings.getInstance(myProject).linkedProjectsSettings.first().externalProjectPath)

    private val scriptConfigurationManager: CompositeScriptConfigurationManager
        get() = ScriptConfigurationManager.getInstance(myProject) as CompositeScriptConfigurationManager

    override fun testDataDirName(): String {
        return "gradleKtsImportTest"
    }

    @Test
    @TargetVersions("6.0.1+")
    fun testEmpty() {
        configureByFiles()
        importProject()

        checkConfiguration("build.gradle.kts")
    }

    @Test
    @TargetVersions("6.0.1+")
    fun testError() {
        var context: ProjectResolverContext? = null
        val contributor =
            ProjectModelContributor { _, _, resolverContext -> context = resolverContext }
        ExtensionTestUtil.maskExtensions(
            ProjectModelContributor.EP_NAME,
            listOf(contributor) + ProjectModelContributor.EP_NAME.extensionList,
            testRootDisposable
        )

        val events = mutableListOf<BuildEvent>()
        val syncViewManager = object : SyncViewManager(myProject) {
            override fun onEvent(buildId: Any, event: BuildEvent) {
                events.add(event)
            }
        }
        myProject.replaceService(SyncViewManager::class.java, syncViewManager, testRootDisposable)

        configureByFiles()

        val result = try {
            importProject()
        } catch (e: AssertionFailedError) {
            e
        }

        assert(result is AssertionFailedError) { "Exception should be thrown" }
        assertNotNull(context)
        assert(context?.cancellationTokenSource?.token()?.isCancellationRequested == true)
        val errors = events.filterIsInstance<MessageEventImpl>().filter { it.kind == MessageEvent.Kind.ERROR }
        val buildScriptErrors = errors.filter { it.group == build_script_errors_group }
        assertTrue(
            "$build_script_errors_group error has not been reported among other errors: $errors",
            buildScriptErrors.isNotEmpty()
        )
    }

    @Test
    @TargetVersions("6.0.1+")
    fun testCompositeBuild() {
        configureByFiles()
        importProject()

        checkConfiguration(
            "settings.gradle.kts",
            "build.gradle.kts",
            "subProject/build.gradle.kts",
            "subBuild/settings.gradle.kts",
            "subBuild/build.gradle.kts",
            "subBuild/subProject/build.gradle.kts",
            "buildSrc/settings.gradle.kts",
            "buildSrc/build.gradle.kts",
            "buildSrc/subProject/build.gradle.kts"
        )
    }

    private fun checkConfiguration(vararg files: String) {
        val scripts = files.map {
            KtsFixture(it).also { kts ->
                assertTrue("Configuration for ${kts.file.path} is missing", scriptConfigurationManager.hasConfiguration(kts.psiFile))
                kts.imported = scriptConfigurationManager.getConfiguration(kts.psiFile)!!
            }
        }

        // reload configuration and check this it is not changed
        scripts.forEach {
            val reloadedConfiguration = scriptConfigurationManager.default.runLoader(
                it.psiFile,
                object : DefaultScriptConfigurationLoader(it.psiFile.project) {
                    override fun shouldRunInBackground(scriptDefinition: ScriptDefinition) = false
                    override fun loadDependencies(
                        isFirstLoad: Boolean,
                        ktFile: KtFile,
                        scriptDefinition: ScriptDefinition,
                        context: ScriptConfigurationLoadingContext
                    ): Boolean {
                        val vFile = ktFile.originalFile.virtualFile
                        val result = getConfigurationThroughScriptingApi(ktFile, vFile, scriptDefinition)
                        context.saveNewConfiguration(vFile, result)
                        return true
                    }
                }
            )
            requireNotNull(reloadedConfiguration)
            // todo: script configuration can have different accessors, need investigation
            // assertTrue(areSimilar(it.imported, reloadedConfiguration))
            it.assertNoSuggestedConfiguration()
        }

        // clear memory cache and check everything loaded from FS
        ScriptConfigurationManager.clearCaches(myProject)
        scripts.forEach {
            val fromFs = scriptConfigurationManager.getConfiguration(it.psiFile)!!
            assertTrue(areSimilar(it.imported, fromFs))
        }
    }

    inner class KtsFixture(val fileName: String) {
        val file = projectDir.resolve(fileName)

        val virtualFile get() = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)!!
        val psiFile get() = myProject.getKtFile(virtualFile)!!

        lateinit var imported: ScriptCompilationConfigurationWrapper

        fun assertNoSuggestedConfiguration() {
            assertFalse(virtualFile.applySuggestedScriptConfiguration(myProject))
        }
    }
}
