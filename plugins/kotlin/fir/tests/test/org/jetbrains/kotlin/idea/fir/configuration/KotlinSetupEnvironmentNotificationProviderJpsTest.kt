// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.configuration

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.registryKeyFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorNotificationPanel
import org.jetbrains.kotlin.idea.configuration.KotlinSetupEnvironmentNotificationProvider
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
class KotlinSetupEnvironmentNotificationProviderJpsTest {
    private val disposableFixture = disposableFixture()
    private val notificationRegistryFixture = registryKeyFixture("kotlin.not.configured.show.notification") { setValue(true) }
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture("main", JavaModuleType.getModuleType().id)
    private val sourceRootFixture = moduleFixture.sourceRootFixture(pathFixture = projectFixture.pathInProjectFixture(Path.of("src")))
    private val kotlinFileFixture = sourceRootFixture.psiFileFixture("Test.kt", "fun test() = Unit")
    private val editorFixture = kotlinFileFixture.editorFixture()

    private val provider = KotlinSetupEnvironmentNotificationProvider()

    @BeforeEach
    fun setUp() {
        notificationRegistryFixture.get()
        setUpProjectJdk()
    }

    @Test
    fun testNotConfiguredBannerShownForJpsModuleWithoutStdlibDependency() {
        assertEquals(
            KotlinProjectConfigurationBundle.message("kotlin.not.configured"),
            getNotificationPanelText()
        )
    }

    @Test
    fun testNotConfiguredBannerHiddenWhileJpsStdlibDependencyIsStillUnresolved() {
        addKotlinStdlibProjectLibrary(resolved = false)

        assertNull(getNotificationPanelText())
    }

    @Test
    fun testNotConfiguredBannerShownAgainOnceJpsStdlibDependencyIsResolved() {
        addKotlinStdlibProjectLibrary(resolved = true)

        assertEquals(
            KotlinProjectConfigurationBundle.message("kotlin.not.configured"),
            getNotificationPanelText()
        )
    }

    private fun addKotlinStdlibProjectLibrary(resolved: Boolean) {
        val libraryName = "kotlin-stdlib-test"
        // Resolved: existing CLASSES root so dependencyFilesExistOnDisk succeeds.
        // Unresolved: non-existent JAR, simulating a repository library that is still
        // being downloaded.
        val rootUrl = if (resolved) {
            VfsUtil.pathToUrl(Files.createTempDirectory("kotlin-stdlib-test-root").toString())
        } else {
            "jar://${projectFixture.get().basePath}/unresolved/$libraryName.jar!/"
        }

        runInEdtAndWait {
            runWriteAction {
                val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(projectFixture.get())
                val library = libraryTable.createLibrary(libraryName) as LibraryEx
                val modifiableModel = library.modifiableModel
                modifiableModel.addRoot(rootUrl, OrderRootType.CLASSES)
                modifiableModel.commit()

                ModuleRootModificationUtil.updateModel(moduleFixture.get()) { model ->
                    model.addLibraryEntry(library)
                }
            }
        }
    }

    private fun getNotificationPanelText(): String? {
        editorFixture.get()

        val project = projectFixture.get()
        val kotlinFile = kotlinFileFixture.get()
        val selectedEditor = FileEditorManager.getInstance(project).selectedEditor
            ?: error("file ${kotlinFile.virtualFile.path} has to be in the editor")
        val panelFactory = runReadActionBlocking {
            provider.collectNotificationData(project, kotlinFile.virtualFile)
        } ?: return null
        val panel = panelFactory.apply(selectedEditor) as? EditorNotificationPanel ?: return null
        return panel.text
    }

    private fun setUpProjectJdk() {
        val project = projectFixture.get()
        val module = moduleFixture.get()
        val disposable = disposableFixture.get()
        val jdk = IdeaTestUtil.getMockJdk17()

        // See the comment in addUnresolvedKotlinStdlibProjectLibrary: setModuleSdk
        // also routes through ModuleRootModificationUtil's invokeAndWait internally,
        // so the whole write action must genuinely run on the EDT, not just under
        // the write lock from an arbitrary background thread.
        runInEdtAndWait {
            runWriteAction {
                ProjectJdkTable.getInstance(project).addJdk(jdk, disposable)
                ProjectRootManager.getInstance(project).projectSdk = jdk
                ModuleRootModificationUtil.setModuleSdk(module, jdk)
            }
        }

        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }
}
