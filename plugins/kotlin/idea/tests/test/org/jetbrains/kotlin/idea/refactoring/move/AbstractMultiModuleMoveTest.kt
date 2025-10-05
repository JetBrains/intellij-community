// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.move

import com.google.gson.JsonObject
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.kotlin.idea.refactoring.rename.loadTestConfiguration
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinMultiFileTestCase
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase
import java.io.File

abstract class AbstractMultiModuleMoveTest : KotlinMultiFileTestCase() {
    override fun getTestRoot(): String = "/refactoring/moveMultiModule/"

    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR

    protected abstract fun runRefactoring(path: String, config: JsonObject, rootDir: VirtualFile, project: Project)

    fun doTest(path: String) {
        val config = loadTestConfiguration(File(path))

        val isEnabled = config.get("enabledIn${pluginMode.name}").asBoolean
        if (!isEnabled) return

        isMultiModule = true

        doTestCommittingDocuments { rootDir, _ ->
            withConfiguredRuntime(config) {
               runRefactoring(path, config, rootDir, project)
            }
        }
    }
}

fun KotlinMultiFileTestCase.withConfiguredRuntime(config: JsonObject, action: KotlinMultiFileTestCase.() -> Unit) {
    PluginTestCaseBase.addJdk(testRootDisposable, IdeaTestUtil::getMockJdk18)

    val withRuntime = config["withRuntime"]?.asBoolean ?: false

    val modulesWithJvmRuntime: List<Module>
    val modulesWithJsRuntime: List<Module>
    val modulesWithCommonRuntime: List<Module>

    if (withRuntime) {
        val moduleManager = ModuleManager.getInstance(project)
        modulesWithJvmRuntime = (config["modulesWithRuntime"]?.asJsonArray?.map { moduleManager.findModuleByName(it.asString!!)!! }
            ?: moduleManager.modules.toList())
        modulesWithJvmRuntime.forEach { ConfigLibraryUtil.configureKotlinRuntimeAndSdk(it, IdeaTestUtil.getMockJdk18()) }

        modulesWithJsRuntime =
            (config["modulesWithJsRuntime"]?.asJsonArray?.map { moduleManager.findModuleByName(it.asString!!)!! } ?: emptyList())
        modulesWithJsRuntime.forEach { module -> ConfigLibraryUtil.configureKotlinStdlibJs(module) }

        modulesWithCommonRuntime =
            (config["modulesWithCommonRuntime"]?.asJsonArray?.map { moduleManager.findModuleByName(it.asString!!)!! }
                ?: emptyList())
        modulesWithCommonRuntime.forEach { ConfigLibraryUtil.configureKotlinStdlibCommon(it) }
    } else {
        modulesWithJvmRuntime = emptyList()
        modulesWithJsRuntime = emptyList()
        modulesWithCommonRuntime = emptyList()
    }

    try {
        action()
    } finally {
        modulesWithJvmRuntime.forEach {
            ConfigLibraryUtil.unConfigureKotlinRuntimeAndSdk(it, IdeaTestUtil.getMockJdk18())
        }
        modulesWithJsRuntime.forEach {
            ConfigLibraryUtil.unConfigureKotlinJsRuntimeAndSdk(it, IdeaTestUtil.getMockJdk18())
        }
        modulesWithCommonRuntime.forEach {
            ConfigLibraryUtil.unConfigureKotlinCommonRuntime(it)
        }
    }
}
