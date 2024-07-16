// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.inspections.tests

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.project.modules
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

                    runInspection(
                        Class.forName(config.getString("k2InspectionClass")), project,
                        withTestDir = configFile.parent
                    )
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
