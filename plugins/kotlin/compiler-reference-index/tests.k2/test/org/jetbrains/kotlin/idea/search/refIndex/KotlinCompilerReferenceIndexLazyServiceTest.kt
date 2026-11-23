// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.CompilerReferenceService
import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.CompilerTester
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.VfsTestUtil.createFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

/**
 * The tests run in a headless environment, where a build must not create the service.
 */
@TestApplication
internal class KotlinCompilerReferenceIndexLazyServiceTest {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val project get() = projectFixture.get()
    private val buildEvents get() = project.messageBus.syncPublisher(BuildManagerListener.TOPIC)

    @Test
    fun `a build does not create the service in a headless environment`() {
        val sessionId = UUID.randomUUID()
        buildEvents.buildStarted(project, sessionId, false)
        buildEvents.buildFinished(project, sessionId, false)
        assertNull(project.serviceIfCreated<KotlinCompilerReferenceIndexService>())
    }

    @Test
    fun `a created service gets the build events`() {
        val service = KotlinCompilerReferenceIndexService[project]
        val sessionId = UUID.randomUUID()
        buildEvents.buildStarted(project, sessionId, false)
        assertTrue(service.isBuildActive())
        buildEvents.buildFinished(project, sessionId, false)
        assertFalse(service.isBuildActive())
    }

    @Test
    fun `a disabled index keeps mixed language usages after it is enabled`() {
        checkMixedLanguageUsages(createAfterFirstBuild = false)
    }

    @Test
    fun `a service created after a build finds mixed language usages after the next build`() {
        checkMixedLanguageUsages(createAfterFirstBuild = true)
    }

    private fun checkMixedLanguageUsages(createAfterFirstBuild: Boolean) = timeoutRunBlocking(timeout = 2.minutes) {
        val module = edtWriteAction {
            val module = ModuleManager.getInstance(project).newModule("${project.basePath}/main.iml", "JAVA_MODULE")
            val root = VfsUtil.createDirectories("${project.basePath}/src")
            ModuleRootModificationUtil.updateModel(module) { it.addContentEntry(root).addSourceFolder(root, false) }
            createFile(root, "Base.java", "public class Base { public void ping() {} }")
            createFile(root, "Middle.kt", "open class Middle : Base()")
            createFile(root, "Leaf.java", "public class Leaf extends Middle {}")
            createFile(root, "Use.java", "class Use { void use() { new Leaf().ping(); } }")
            ConfigLibraryUtil.configureKotlinRuntime(module)
            module
        }
        val compiler = withContext(Dispatchers.EDT) { CompilerTester(module) }
        val wasEnabled = AdvancedSettings.getBoolean("kotlin.compiler.ref.index")
        try {
            AdvancedSettings.setBoolean("kotlin.compiler.ref.index", false)
            val javaIndex = CompilerReferenceService.getInstance(project)
            assertNull(project.serviceIfCreated<KotlinCompilerReferenceIndexService>())
            if (!createAfterFirstBuild) {
                KotlinCompilerReferenceIndexService[project]
            }
            rebuild(compiler)
            if (createAfterFirstBuild) {
                assertNull(project.serviceIfCreated<KotlinCompilerReferenceIndexService>())
            }
            AdvancedSettings.setBoolean("kotlin.compiler.ref.index", true)
            KotlinCompilerReferenceIndexService[project]
            if (createAfterFirstBuild) {
                rebuild(compiler)
            }
            IndexingTestUtil.suspendUntilIndexesAreReady(project)
            readAction {
                val base = JavaPsiFacade.getInstance(project).findClass("Base", GlobalSearchScope.projectScope(project))!!
                val method = base.findMethodsByName("ping", false).single()
                val useFile = JavaPsiFacade.getInstance(project).findClass("Use", GlobalSearchScope.projectScope(project))!!
                    .containingFile.virtualFile
                val scope = javaIndex.getScopeWithCodeReferences(method)
                assertNotNull(scope, "The Java compiler index must participate in the search")
                assertTrue(scope!!.contains(useFile), "The Java compiler index must include Use.java")
                val usageFiles = ReferencesSearch.search(method).findAll().map { it.element.containingFile.name }
                assertEquals(listOf("Use.java"), usageFiles)
            }
        } finally {
            AdvancedSettings.setBoolean("kotlin.compiler.ref.index", wasEnabled)
            withContext(NonCancellable + Dispatchers.EDT) { compiler.tearDown() }
        }
    }

    private fun rebuild(compiler: CompilerTester) {
        val errors = compiler.rebuild().filter { it.category == CompilerMessageCategory.ERROR }
        assertTrue(errors.isEmpty(), errors.joinToString("\n") { it.message })
    }
}
