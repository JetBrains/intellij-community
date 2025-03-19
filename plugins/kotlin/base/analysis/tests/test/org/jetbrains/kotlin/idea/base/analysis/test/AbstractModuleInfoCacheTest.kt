// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.analysis.test

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.JavaModuleTestCase
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleProductionSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleTestSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.caches.project.IdeaModelInfosCache
import java.io.File

abstract class AbstractModuleInfoCacheTest : JavaModuleTestCase() {
    override fun setUp() {
        super.setUp()

        getOrCreateProjectBaseDir()
    }

    protected fun renameModule(module: Module, newName: String) {
        runWriteActionAndWait {
            ModuleManager.getInstance(project).getModifiableModel().apply {
                renameModule(module, newName)
                commit()
            }
        }
    }

    protected fun removeModule(module: Module) {
        runWriteActionAndWait {
            ModuleManager.getInstance(project).getModifiableModel().apply {
                disposeModule(module)
                commit()
            }
        }
    }

    @OptIn(K1ModeProjectStructureApi::class)
    protected fun assertHasModules(vararg names: String) {
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

    protected fun createModule(name: String, block: ModuleModificationContext.() -> Unit): Module {
        return createModule(name).also { module ->
            ModuleRootModificationUtil.updateModel(module) { model ->
                val moduleDir = File(projectDir, name)
                val contentEntry = model.addContentEntry(VfsUtilCore.pathToUrl(moduleDir.path))
                ModuleModificationContext(moduleDir, contentEntry).block()
            }
        }
    }

    protected fun updateModule(module: Module, block: ModuleModificationContext.() -> Unit) {
        ModuleRootModificationUtil.updateModel(module) { model ->
            val moduleDir = File(projectDir, module.name)
            val contentEntry = model.contentEntries.single()
            ModuleModificationContext(moduleDir, contentEntry).block()
        }
    }

    private val projectDir: File
        get() {
            val projectPath = project.basePath ?: error("Project base directory path is not configured")
            return File(projectPath)
        }

    protected class ModuleModificationContext(private val moduleDir: File, private val contentEntry: ContentEntry) {
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
