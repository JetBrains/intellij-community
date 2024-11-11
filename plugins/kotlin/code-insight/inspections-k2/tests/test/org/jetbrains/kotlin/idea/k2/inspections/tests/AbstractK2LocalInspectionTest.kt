// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.inspections.tests

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.registerExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.k2FileName
import org.jetbrains.kotlin.idea.core.script.SCRIPT_CONFIGURATIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.k2.BaseScriptModel
import org.jetbrains.kotlin.idea.core.script.k2.BundledScriptConfigurationsSource
import org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurations
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.inspections.AbstractLocalInspectionTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile
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

        if ((myFixture.file as? KtFile)?.isScript() == true) {
            val dependenciesSource = object : BundledScriptConfigurationsSource(project, CoroutineScope(Dispatchers.IO + SupervisorJob())) {
                override suspend fun updateModules(storage: MutableEntityStorage?) {
                    //do nothing because adding modules is not permitted in light tests
                }
            }
            project.registerExtension(SCRIPT_CONFIGURATIONS_SOURCES, dependenciesSource, testRootDisposable)

            val script = BaseScriptModel(psiFile.virtualFile)
            runWithModalProgressBlocking(project, "Testing") {
                dependenciesSource.updateDependenciesAndCreateModules(setOf(script))
            }
        }
        super.doTest(path)
    }
}