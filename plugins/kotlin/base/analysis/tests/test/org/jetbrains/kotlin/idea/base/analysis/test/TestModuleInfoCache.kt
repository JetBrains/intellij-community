// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.test

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModuleRootModificationUtil.updateModel
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.JavaModuleTestCase
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleProductionSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleTestSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.idea.caches.project.IdeaModelInfosCache
import java.io.File

class TestModuleInfoCache : JavaModuleTestCase() {
    override fun setUp() {
        super.setUp()

        getOrCreateProjectBaseDir()
    }

    fun testSimple() {
        assertHasModules("SDK")

        val fooModule = createModule("foo") {
            addSourceFolder("src", isTest = false)
            addSourceFolder("test", isTest = true)
        }

        assertHasModules("SDK", "foo:src", "foo:test")

        val barModule = createModule("bar") {
            // No source folders
        }

        assertHasModules("SDK", "foo:src", "foo:test")

        updateModule(fooModule) {
            removeSourceFolder("src")
        }

        assertHasModules("SDK", "foo:test")

        updateModule(barModule) {
            addSourceFolder("test", isTest = true)
        }

        assertHasModules("SDK", "foo:test", "bar:test")

        val bazModule = createModule("baz") {
            addSourceFolder("src", isTest = false)
            addSourceFolder("test", isTest = true)
            addSourceFolder("performanceTest", isTest = true)
        }

        assertHasModules("SDK", "foo:test", "bar:test", "baz:src", "baz:test")

        renameModule(bazModule, "boo")

        assertHasModules("SDK", "foo:test", "bar:test", "boo:src", "boo:test")

        renameModule(barModule, "baq")

        assertHasModules("SDK", "foo:test", "baq:test", "boo:src", "boo:test")

        removeModule(barModule)

        assertHasModules("SDK", "foo:test", "boo:src", "boo:test")
    }

    private fun assertHasModules(vararg names: String) {
        val moduleInfos = buildList {
            val cache = project.service<IdeaModelInfosCache>()
            for (moduleInfo in cache.allModules()) {
                when (moduleInfo) {
                    is ModuleProductionSourceInfo -> add(moduleInfo.module.name + ":src")
                    is ModuleTestSourceInfo -> add(moduleInfo.module.name + ":test")
                    is SdkInfo -> add("SDK")
                    else -> error("Unexpected module info kind: ${moduleInfo.javaClass}")
                }
            }
        }

        assertEquals(names.sorted(), moduleInfos.sorted())
    }

    private fun createModule(name: String, block: ModuleModificationContext.() -> Unit): Module {
        return createModule(name).also { module ->
            updateModel(module) { model ->
                val moduleDir = File(projectDir, name)
                val contentEntry = model.addContentEntry(VfsUtilCore.pathToUrl(moduleDir.path))
                ModuleModificationContext(moduleDir, contentEntry).block()
            }
        }
    }

    private fun updateModule(module: Module, block: ModuleModificationContext.() -> Unit) {
        updateModel(module) { model ->
            val moduleDir = File(projectDir, module.name)
            val contentEntry = model.contentEntries.single()
            ModuleModificationContext(moduleDir, contentEntry).block()
        }
    }

    private fun renameModule(module: Module, newName: String) {
        runWriteActionAndWait {
            ModuleManager.getInstance(project).getModifiableModel().apply {
                renameModule(module, newName)
                commit()
            }
        }
    }

    private fun removeModule(module: Module) {
        runWriteActionAndWait {
            ModuleManager.getInstance(project).getModifiableModel().apply {
                disposeModule(module)
                commit()
            }
        }
    }

    private val projectDir: File
        get() {
            val projectPath = project.basePath ?: error("Project base directory path is not configured")
            return File(projectPath)
        }

    private class ModuleModificationContext(private val moduleDir: File, private val contentEntry: ContentEntry) {
        fun addSourceFolder(name: String, isTest: Boolean) {
            val sourceFolderPath = File(moduleDir, name).path
            contentEntry.addSourceFolder(VfsUtilCore.pathToUrl(sourceFolderPath), isTest)
        }

        fun removeSourceFolder(name: String) {
            val sourceFolder = contentEntry.sourceFolders.firstOrNull { it.url.endsWith("/$name") }
                ?: error("Source folder \"$name\" not found")

            contentEntry.removeSourceFolder(sourceFolder)
        }
    }
}