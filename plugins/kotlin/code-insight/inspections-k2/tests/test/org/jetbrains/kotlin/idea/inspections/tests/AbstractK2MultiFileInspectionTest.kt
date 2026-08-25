// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections.tests

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.kotlin.idea.base.util.getString
import org.jetbrains.kotlin.idea.inspections.runInspection
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinMultiFileTestCase
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase.addJdk
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase.fullJdk
import java.io.File

abstract class AbstractK2MultiFileInspectionTest : KotlinMultiFileTestCase() {

    init {
        myDoCompare = false
    }

    protected fun doTest(path: String) {
        val configFile = File(path)
        val config = JsonParser.parseString(FileUtil.loadFile(configFile, true)) as JsonObject

        val withRuntime = config["withRuntime"]?.asBoolean ?: false
        val withFullJdk = config["withFullJdk"]?.asBoolean ?: false
        isMultiModule = config["isMultiModule"]?.asBoolean ?: false
        val settings = configFile.resolveSibling("settings.xml").takeIf { it.exists() }?.let(JDOMUtil::load)
        val expectedFixes = config["expectedFixes"]?.asJsonArray?.map { it.asString }

        doTest(
            { _, _ ->
                val sdk = if (withFullJdk) fullJdk() else IdeaTestUtil.getMockJdk18()
                addJdk(testRootDisposable) { sdk }

                try {
                    if (withRuntime) {
                        project.modules.forEach { module ->
                            ConfigLibraryUtil.configureKotlinRuntimeAndSdk(module, sdk)
                        }
                    }

                    val presentation = runInspection(
                        Class.forName(config.getString("k2InspectionClass")), project,
                        settings = settings,
                        withTestDir = configFile.parent
                    )
                    if (expectedFixes != null) {
                        val actualFixes = presentation.problemDescriptors
                            .flatMap { descriptor -> descriptor.fixes?.map { it.name } ?: emptyList() }
                        assertEquals(expectedFixes.sorted(), actualFixes.sorted())
                    }
                } finally {
                    if (withRuntime) {
                        project.modules.forEach { module ->
                            ConfigLibraryUtil.unConfigureKotlinRuntimeAndSdk(module, sdk)
                        }
                    }
                }
            },
            getTestDirName(true)
        )
    }

    override fun getTestRoot(): String = "/multiFileInspections/"

    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR
}
