// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit3RunnerWithInners::class)
abstract class AbstractDefinitionFromDependenciesProviderTest : HeavyPlatformTestCase() {

    

    override fun runInDispatchThread(): Boolean = false

    fun doTest(path: String) {
        val projectRoot = getOrCreateProjectBaseDir()
        val testDataDir = File(path)

        copyDirContentsTo(
            LocalFileSystem.getInstance().findFileByIoFile(testDataDir)
                ?: error("Cannot find testdata dir at $path"),
            projectRoot
        )

        val testModules = testDataDir.listFiles { f -> f.name.startsWith("module") }
            ?.map { createTestModule(it) }
            .orEmpty()

        val jars = testDataDir.listFiles { f -> f.name.startsWith("jar") }
            ?.map { packJar(it) }
            .orEmpty()

        testModules.forEach { module ->
            jars.forEach { jar ->
                PsiTestUtil.addLibrary(module, jar.absolutePath)
            }
        }

        val provider = DefinitionFromDependenciesProvider(project)
        val discoveredFqns = provider.getDefinitionClasses().toList()
        val discoveredClasspath = provider.getDefinitionsClassPath().map { it.path }

        val fileText = File(path, "test.kts").readText()
        val expectedNames = InTextDirectivesUtils.findListWithPrefixes(fileText, "// NAME:").sorted()
        val expectedClasspath = InTextDirectivesUtils.findListWithPrefixes(fileText, "// CLASSPATH:").sorted()

        assertOrderedEquals("Discovered template FQNs", discoveredFqns.sorted(), expectedNames)
        assertOrderedEquals(
            "Discovered classpath",
            discoveredClasspath.map { it.removeTestDirPrefix() }.sorted(),
            expectedClasspath
        )
    }

    private fun String.removeTestDirPrefix(): String = substringAfterLast(getTestName(true))

    private fun createTestModule(dir: File): Module {
        val newModule = createModuleAt("${name}_${dir.name}", project, JavaModuleType.getModuleType(), dir.toPath())
        dir.listFiles()?.forEach {
            val root = VfsUtil.findFileByIoFile(it, true) ?: return@forEach
            when (it.name) {
                "src" -> PsiTestUtil.addSourceRoot(newModule, root)
                "test" -> PsiTestUtil.addSourceRoot(newModule, root, true)
                "resources" -> PsiTestUtil.addSourceRoot(newModule, root, JavaResourceRootType.RESOURCE)
            }
        }
        return newModule
    }

    private fun packJar(dir: File): File {
        val contentDir = KotlinTestUtils.tmpDirForReusableFolder("folderForLibrary-${getTestName(true)}").also {
            it.mkdirs()
        }
        VfsRootAccess.allowRootAccess(testRootDisposable, contentDir.absolutePath)
        return IoTestUtil.createTestJar(File(contentDir, "templates.jar"), dir)
    }
}
