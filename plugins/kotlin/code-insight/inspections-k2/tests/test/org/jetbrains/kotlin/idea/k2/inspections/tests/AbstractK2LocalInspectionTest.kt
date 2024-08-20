// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.inspections.tests

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.registerExtension
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.k2FileName
import org.jetbrains.kotlin.idea.core.script.SCRIPT_DEPENDENCIES_SOURCES
import org.jetbrains.kotlin.idea.core.script.k2.ScriptDependenciesData
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.gradleJava.scripting.GradleScriptDependenciesSource
import org.jetbrains.kotlin.idea.gradleJava.scripting.GradleScriptModel
import org.jetbrains.kotlin.idea.inspections.AbstractLocalInspectionTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists

abstract class AbstractK2LocalInspectionTest : AbstractLocalInspectionTest() {

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override val inspectionFileName: String = ".k2Inspection"

    override fun checkForUnexpectedErrors(fileText: String) {}

    override fun fileName(): String = k2FileName(super.fileName(), testDataDirectory)

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() }
        )
    }

    override fun getAfterTestDataAbsolutePath(mainFileName: String): Path {
        val k2Extension = IgnoreTests.FileExtension.K2
        val k2FileName = mainFileName.removeSuffix(".kt").removeSuffix(".$k2Extension") + ".$k2Extension.kt.after"
        val k2FilePath = testDataDirectory.toPath() / k2FileName
        if (k2FilePath.exists()) return k2FilePath

        return super.getAfterTestDataAbsolutePath(mainFileName)
    }

    override fun doTestFor(mainFile: File, inspection: LocalInspectionTool, fileText: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(mainFile.toPath(), IgnoreTests.DIRECTIVES.IGNORE_K2, "after") {
            doTestForInternal(mainFile, inspection, fileText)
        }
    }

    override fun doTest(path: String) {
        val mainFile = File(dataFilePath(fileName()))

        val extraFileNames = findExtraFilesForTest(mainFile)

        val psiFile = myFixture.configureByFiles(*(listOf(mainFile.name) + extraFileNames).toTypedArray()).first()

        val dependenciesSource = object : GradleScriptDependenciesSource(project) {
            override suspend fun updateModules(
                dependencies: ScriptDependenciesData,
                storage: MutableEntityStorage?
            ) {
                //do nothing because adding modules is not permitted in light tests
            }
        }
        project.registerExtension(SCRIPT_DEPENDENCIES_SOURCES, dependenciesSource, testRootDisposable)

        val script = GradleScriptModel(psiFile.virtualFile)
        runWithModalProgressBlocking(project, "Testing") {
            dependenciesSource.updateDependenciesAndCreateModules(setOf(script))
        }
        super.doTest(path)
    }
}