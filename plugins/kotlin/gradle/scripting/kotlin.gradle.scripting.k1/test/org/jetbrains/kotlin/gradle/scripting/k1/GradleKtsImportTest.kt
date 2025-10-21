// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.gradle.scripting.k1

import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.testFramework.replaceService
import junit.framework.AssertionFailedError
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradleImportingTestCase
import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.k1.applySuggestedScriptConfiguration
import org.jetbrains.kotlin.idea.core.script.k1.areSimilar
import org.jetbrains.kotlin.idea.core.script.k1.configuration.loader.DefaultScriptConfigurationLoader
import org.jetbrains.kotlin.idea.core.script.k1.configuration.loader.ScriptConfigurationLoadingContext
import org.jetbrains.kotlin.idea.core.script.k1.ucache.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k1.ucache.KotlinScriptLibraryRootTypeId
import org.jetbrains.kotlin.idea.core.script.k1.ucache.listDependencies
import org.jetbrains.kotlin.idea.core.script.v1.getKtFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.File
import kotlin.test.assertFailsWith

@RunWith(value = Parameterized::class)
@Suppress("ACCIDENTAL_OVERRIDE")
abstract class GradleKtsImportTest : KotlinGradleImportingTestCase() {
    companion object {
        @JvmStatic
        @Suppress("ACCIDENTAL_OVERRIDE")
        @Parameters(name = "{index}: with Gradle-{0}")
        fun data(): Collection<Array<Any?>> = listOf(arrayOf("6.0.1"))
    }

    val projectDir: File get() = File(GradleSettings.getInstance(myProject).linkedProjectsSettings.first().externalProjectPath)

    internal val scriptConfigurationManager: ScriptConfigurationManager
        get() = ScriptConfigurationManager.getInstance(myProject)

    override fun testDataDirName(): String = "gradleKtsImportTest"


    class WorkspaceModelSyncTest : GradleKtsImportTest() {

        @Test
        @TargetVersions("6.0.1+")
        fun testWorkspaceModelInSyncAfterImport() {
            configureByFiles()
            importProject()

            checkEquivalence("build.gradle.kts")
            checkEquivalence("settings.gradle.kts")
        }

        private fun checkEquivalence(fileName: String) {
            val ktsFile = KtsFixture(fileName).virtualFile

            val (managerClassFiles, managerSourceFiles) = getDependenciesFromManager(ktsFile)

            val entityStorage = WorkspaceModel.getInstance(myProject).currentSnapshot
            val scriptEntity = entityStorage.entities(KotlinScriptEntity::class.java).find { it.path.contains(fileName) }
                ?: error("Workspace model is unaware of script $fileName")

            val entityClassFiles = scriptEntity.listDependencies(myProject, KotlinScriptLibraryRootTypeId.COMPILED)
            val entitySourceFiles = scriptEntity.listDependencies(myProject, KotlinScriptLibraryRootTypeId.SOURCES)

            assertEquals("Class dependencies for $fileName are not equivalent", entityClassFiles, managerClassFiles)
            assertEquals("Source dependencies for $fileName are not equivalent", entitySourceFiles, managerSourceFiles)
        }

        // classes, sources
        private fun getDependenciesFromManager(file: VirtualFile): Pair<Collection<VirtualFile>, Collection<VirtualFile>> {
            val managerClassFiles = scriptConfigurationManager.getScriptDependenciesClassFiles(file)
            val managerSourceFiles = scriptConfigurationManager.getScriptDependenciesSourceFiles(file)
            return Pair(managerClassFiles, managerSourceFiles)
        }
    }



    class Empty : GradleKtsImportTest() {
        @Test
        @TargetVersions("6.0.1+")
        fun testEmpty() {
            configureByFiles()
            importProject()

            checkConfiguration("build.gradle.kts")
        }
    }

    class Error : GradleKtsImportTest() {
        @Test
        @TargetVersions("6.0.1+")
        fun testError() {
            val events = mutableListOf<BuildEvent>()
            val syncViewManager = object : SyncViewManager(myProject) {
                override fun onEvent(buildId: Any, event: BuildEvent) {
                    events.add(event)
                }
            }
            myProject.replaceService(SyncViewManager::class.java, syncViewManager, testRootDisposable)

            configureByFiles()

            assertFailsWith<AssertionFailedError> { importProject() }

            val expectedErrorMessage = "Unresolved reference: unresolved"
            val errors = events.filterIsInstance<MessageEventImpl>().filter { it.kind == MessageEvent.Kind.ERROR }
            val buildScriptErrors = errors.filter { it.message == expectedErrorMessage }
            assertTrue(
              "$expectedErrorMessage error has not been reported among other errors: $errors",
              buildScriptErrors.isNotEmpty()
            )
        }
    }

    class CompositeBuild2 : GradleKtsImportTest() {
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
    }

    protected fun checkConfiguration(vararg files: String) {
        val scripts = files.map {
            KtsFixture(it).also { kts ->
                assertTrue("Configuration for ${kts.file.path} is missing",
                                    scriptConfigurationManager.hasConfiguration(kts.psiFile))
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

    inner class KtsFixture(fileName: String) {
        val file = projectDir.resolve(fileName)

        val virtualFile get() = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)!!
        val psiFile get() = myProject.getKtFile(virtualFile)!!

        lateinit var imported: ScriptCompilationConfigurationWrapper

        fun assertNoSuggestedConfiguration() {
            assertFalse(virtualFile.applySuggestedScriptConfiguration(myProject))
        }
    }
}
