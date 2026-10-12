// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.server.BuildManagerListener
import com.intellij.compiler.server.CustomBuilderMessageHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class KotlinCompilerReferenceIndexDirtyScopeTest {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleA = projectFixture.moduleFixture("a", "JAVA_MODULE")
    private val moduleB = projectFixture.moduleFixture("b", "JAVA_MODULE")
    private val moduleC = projectFixture.moduleFixture("c", "JAVA_MODULE")
    private val moduleD = projectFixture.moduleFixture("d", "JAVA_MODULE")
    private val moduleE = projectFixture.moduleFixture("e", "JAVA_MODULE")
    private val project get() = projectFixture.get()
    private lateinit var modules: List<Module>
    private lateinit var files: List<VirtualFile>
    private lateinit var service: KotlinCompilerReferenceIndexService
    private val storage = RecordingStorage()

    @TestDisposable
    private lateinit var disposable: Disposable

    @BeforeEach
    fun setUp() = timeoutRunBlocking {
        modules = listOf(moduleA.get(), moduleB.get(), moduleC.get(), moduleD.get(), moduleE.get())
        files = edtWriteAction {
            modules.mapIndexed { index, module ->
                val root = VfsUtil.createDirectories("${project.basePath}/${module.name}")
                ModuleRootModificationUtil.updateModel(module) { it.addContentEntry(root).addSourceFolder(root, false) }
                root.createChildData(this, "Source.kt").also {
                    VfsUtil.saveText(it, if (index == 0) "class Target" else "class Source$index")
                }
            }
        }
        storage.usages = listOf(files[1])
        KotlinCompilerReferenceIndexStorageProvider.EP_NAME.point.registerExtension(
            object : KotlinCompilerReferenceIndexStorageProvider {
                override val builderId: String = "test"
                override fun isApplicable(project: Project): Boolean = true
                override fun hasIndex(project: Project): Boolean = true
                override fun createStorage(project: Project, projectPath: String): KotlinCompilerReferenceIndexStorage = storage
            },
            LoadingOrder.FIRST,
            disposable
        )
        service = KotlinCompilerReferenceIndexService[project]
        IndexingTestUtil.suspendUntilIndexesAreReady(project)
    }

    @Test
    fun `skips index queries at the dirty module threshold`() {
        compile(modules.take(2))
        runReadActionBlocking {
            assertNull(service.scopeWithCodeReferences(target()))
        }
        assertEquals(0, storage.usageQueries)
    }

    @Test
    fun `keeps indexed references and dirty files below the threshold`() {
        compile(modules.take(3))
        runReadActionBlocking {
            val scope = service.scopeWithCodeReferences(target())
            assertNotNull(scope)
            assertFalse(scope!!.contains(files[0]))
            assertFalse(scope.contains(files[2]))
            for (file in listOf(files[1]) + files.drop(3)) {
                assertTrue(scope.contains(file), file.path)
            }
        }
        assertEquals(1, storage.usageQueries)
    }

    @Test
    fun `resumes index queries after more modules compile`() {
        compile(modules.take(2))
        runReadActionBlocking { assertNull(service.scopeWithCodeReferences(target())) }
        compile(listOf(modules[2]))
        runReadActionBlocking { assertNotNull(service.scopeWithCodeReferences(target())) }
        assertEquals(1, storage.usageQueries)
    }

    @Test
    fun `includes clean dependents of dirty modules`() {
        ModuleRootModificationUtil.addDependency(modules[1], modules[3])
        storage.usages = emptyList()
        compile(modules.take(3))
        runReadActionBlocking {
            val scope = service.scopeWithCodeReferences(target())!!
            assertTrue(scope.contains(files[1]))
        }
    }

    @Test
    fun `checks the threshold after an unsaved edit`() = timeoutRunBlocking {
        compile(modules.take(3))
        readAction { assertNotNull(service.scopeWithCodeReferences(target())) }
        edtWriteAction {
            WriteCommandAction.runWriteCommandAction(project) {
                FileDocumentManager.getInstance().getDocument(files[1])!!.insertString(0, "// Edited\n")
            }
        }
        readAction { assertNull(service.scopeWithCodeReferences(target())) }
        assertEquals(1, storage.usageQueries)
    }

    @Test
    fun `preserves usages with index filtering and the fallback`() = timeoutRunBlocking {
        for (module in modules.drop(1)) {
            ModuleRootModificationUtil.addDependency(module, modules[0])
        }
        edtWriteAction {
            for (file in files.drop(1)) {
                VfsUtil.saveText(file, "val value: Target? = null")
            }
        }
        IndexingTestUtil.suspendUntilIndexesAreReady(project)
        storage.usages = listOf(files[1], files[2])
        compile(modules.take(3))
        val indexedUsages = readAction { usageFiles() }
        assertEquals(files.drop(1).toSet(), indexedUsages)
        edtWriteAction { VfsUtil.saveText(files[1], "val value: Target? = null\n// Edited") }
        IndexingTestUtil.suspendUntilIndexesAreReady(project)
        readAction {
            assertNull(service.scopeWithCodeReferences(target()))
            assertEquals(indexedUsages, usageFiles())
        }
        val wasEnabled = AdvancedSettings.getBoolean("kotlin.compiler.ref.index")
        try {
            AdvancedSettings.setBoolean("kotlin.compiler.ref.index", false)
            readAction { assertEquals(indexedUsages, usageFiles()) }
        } finally {
            AdvancedSettings.setBoolean("kotlin.compiler.ref.index", wasEnabled)
        }
    }

    @Test
    fun `falls back while the storage write lock is held`() = timeoutRunBlocking {
        compile(modules.take(3))
        val closing = CountDownLatch(1)
        val release = CountDownLatch(1)
        storage.closeAction = {
            closing.countDown()
            assertTrue(release.await(5, TimeUnit.SECONDS))
        }
        val build = launch(Dispatchers.Default) {
            project.messageBus.syncPublisher(BuildManagerListener.TOPIC).buildStarted(project, UUID.randomUUID(), false)
        }
        try {
            withContext(Dispatchers.IO) { assertTrue(closing.await(5, TimeUnit.SECONDS)) }
            val query = async(Dispatchers.Default) { readAction { service.scopeWithCodeReferences(target()) } }
            assertNull(withTimeout(2.seconds) { query.await() })
        } finally {
            release.countDown()
            build.join()
            storage.closeAction = {}
        }
    }

    private fun usageFiles(): Set<VirtualFile> = ReferencesSearch.search(target()).findAll()
        .mapTo(mutableSetOf()) { it.element.containingFile.virtualFile }

    private fun target() = (PsiManager.getInstance(project).findFile(files[0]) as KtFile).declarations.single()

    private fun compile(compiledModules: List<Module>) {
        val sessionId = UUID.randomUUID()
        val buildEvents = project.messageBus.syncPublisher(BuildManagerListener.TOPIC)
        buildEvents.buildStarted(project, sessionId, false)
        val messages = project.messageBus.syncPublisher(CustomBuilderMessageHandler.TOPIC)
        for (module in compiledModules) {
            messages.messageReceived("test", "", module.name)
        }
        buildEvents.buildFinished(project, sessionId, false)
    }

    private class RecordingStorage : KotlinCompilerReferenceIndexStorage {
        var usages: List<VirtualFile> = emptyList()
        var usageQueries: Int = 0
        var closeAction: () -> Unit = {}

        override fun getUsages(fqName: FqName): List<VirtualFile> {
            usageQueries++
            return usages
        }

        override fun getSubtypesOf(fqName: FqName, deep: Boolean): Sequence<FqName> = emptySequence()
        override fun close() = closeAction()
    }
}
