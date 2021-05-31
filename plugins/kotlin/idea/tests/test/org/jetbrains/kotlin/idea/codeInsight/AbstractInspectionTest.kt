// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInspection.ex.EntryPointsManagerBase
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.util.ThrowableRunnable
import org.jdom.Document
import org.jdom.input.SAXBuilder
import org.jetbrains.kotlin.formatter.FormatSettingsUtil
import org.jetbrains.kotlin.idea.inspections.runInspection
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.idea.versions.kotlinCompilerVersionShort
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.plugins.groovy.GroovyFileType
import java.io.File

abstract class AbstractInspectionTest : KotlinLightCodeInsightFixtureTestCase() {
    companion object {
        const val ENTRY_POINT_ANNOTATION = "test.anno.EntryPoint"
    }

    override fun setUp() {
        try {
            super.setUp()
            EntryPointsManagerBase.getInstance(project).ADDITIONAL_ANNOTATIONS.add(ENTRY_POINT_ANNOTATION)
            runWriteAction { FileTypeManager.getInstance().associateExtension(GroovyFileType.GROOVY_FILE_TYPE, "gradle") }
        } catch (e: Throwable) {
            TestLoggerFactory.onTestFinished(false)
            throw e
        }
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { EntryPointsManagerBase.getInstance(project).ADDITIONAL_ANNOTATIONS.remove(ENTRY_POINT_ANNOTATION) },
            ThrowableRunnable { super.tearDown() }
        )
    }

    protected open fun configExtra(psiFiles: List<PsiFile>, options: String) {

    }

    protected open val forceUsePackageFolder: Boolean = false //workaround for IDEA-176033

    protected fun doTest(path: String) {
        val optionsFile = File(path)
        val options = FileUtil.loadFile(optionsFile, true)

        val inspectionClass = Class.forName(InTextDirectivesUtils.findStringWithPrefixes(options, "// INSPECTION_CLASS: ")!!)

        val fixtureClasses = InTextDirectivesUtils.findListWithPrefixes(options, "// FIXTURE_CLASS: ")

        withCustomCompilerOptions(options, project, module) {
            val inspectionsTestDir = optionsFile.parentFile!!
            val srcDir = inspectionsTestDir.parentFile!!

            val settingsFile = File(inspectionsTestDir, "settings.xml")
            val settingsElement = if (settingsFile.exists()) {
                (SAXBuilder().build(settingsFile) as Document).rootElement
            } else {
                null
            }

            with(myFixture) {
                testDataPath = srcDir.path

                val afterFiles = srcDir.listFiles { it -> it.name == "inspectionData" }
                    ?.single()
                    ?.listFiles { it -> it.extension == "after" }
                    ?: emptyArray()

                val psiFiles = srcDir.walkTopDown().onEnter { it.name != "inspectionData" }.mapNotNull { file ->
                    when {
                        file.isDirectory -> null
                        file.extension == "kt" -> {
                            val text = FileUtil.loadFile(file, true)
                            val fileText =
                                if (text.lines().any { it.startsWith("package") })
                                    text
                                else
                                    "package ${file.nameWithoutExtension};$text"
                            if (forceUsePackageFolder) {
                                val packageName = fileText.substring(
                                    "package".length,
                                    fileText.indexOfAny(charArrayOf(';', '\n')),
                                ).trim()
                                val projectFileName = packageName.replace('.', '/') + "/" + file.name
                                addFileToProject(projectFileName, fileText)
                            } else {
                                configureByText(file.name, fileText)!!
                            }
                        }

                        file.extension == "gradle" -> {
                            val text = FileUtil.loadFile(file, true)
                            val fileText = text.replace("\$PLUGIN_VERSION", kotlinCompilerVersionShort())
                            configureByText(file.name, fileText)!!
                        }

                        else -> {
                            val filePath = file.relativeTo(srcDir).invariantSeparatorsPath
                            configureByFile(filePath)
                        }
                    }
                }.toList()

                configureCodeStyleAndRun(
                    project,
                    configurator = { FormatSettingsUtil.createConfigurator(options, it).configureSettings() }
                ) {
                    configureRegistryAndRun(options) {
                        try {
                            fixtureClasses.forEach { TestFixtureExtension.loadFixture(it, myFixture.module) }

                            configExtra(psiFiles, options)

                            val presentation = runInspection(
                                inspectionClass, project,
                                settings = settingsElement,
                                files = psiFiles.map { it.virtualFile!! }, withTestDir = inspectionsTestDir.path,
                            )

                            if (afterFiles.isNotEmpty()) {
                                presentation.problemDescriptors.forEach { problem ->
                                    problem.fixes?.forEach { quickFix ->
                                        project.executeWriteCommand(quickFix.name, quickFix.familyName) {
                                            quickFix.applyFix(project, problem)
                                        }
                                    }
                                }

                                for (filePath in afterFiles) {
                                    val kotlinFile = psiFiles.first { filePath.name == it.name + ".after" }
                                    KotlinTestUtils.assertEqualsToFile(filePath, kotlinFile.text)
                                }
                            }
                        } finally {
                            fixtureClasses.forEach { TestFixtureExtension.unloadFixture(it) }
                        }
                    }
                }
            }
        }
    }
}
