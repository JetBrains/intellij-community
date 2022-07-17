// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.projectPropertyBased

import com.intellij.java.propertyBased.BaseUnivocityTest
import com.intellij.lang.properties.PropertiesFileType
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.propertyBased.MadTestingAction
import com.intellij.testFramework.propertyBased.MadTestingUtil
import com.intellij.util.ThrowableRunnable
import com.intellij.util.ui.UIUtil
import org.jetbrains.jetCheck.GenerationEnvironment
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import org.jetbrains.kotlin.idea.KotlinFileType
import java.io.File
import java.io.FileFilter
import java.nio.file.Path
import java.util.function.Function
import java.util.function.Supplier

@SkipSlowTestLocally
class KotlinProjectSanityTest: BaseUnivocityTest() {
    private val seed: String? = System.getProperty("seed")

    override fun doCreateAndOpenProject(): Project {
        val optionBuilder = openProjectOptions
        val projectFile = Path.of(testDataPath)
        return ProjectManagerEx.getInstanceEx().openProject(projectFile, optionBuilder.build()) ?: error("unable to open project $projectFile")
    }

    override fun getTestProjectJdk(): Sdk = JavaAwareProjectJdkTableImpl.getInstanceEx().internalJdk

    override fun projectLanguageLevel(): LanguageLevel = LanguageLevel.JDK_1_8

    override fun getTestDataPath(): String {
        val file = File(System.getProperty("projectPath", PathManager.getHomePath()))
        check(file.exists()) {
            """Cannot find a test project:
  You can use IntelliJ Community for that, execute: `git clone https://github.com/JetBrains/intellij-community.git`
  open the just cloned intellij-community project in IntelliJ IDEA, let it download all the libraries, close the IDE
  execute this in intellij-community directory: `git reset HEAD --hard`
  Rerun ${KotlinProjectSanityTest::javaClass.name} with `-DprojectPath=/path/to/intellij-community`
  """
        }
        return file.absolutePath
    }

    fun testRandomNonModifiableActivity() {
        PropertyChecker.customized()
            .run {
                seed?.let { this.rechecking(it) } ?: this
            }
            .printGeneratedValues()
            .checkScenarios(
                doActionsOnFiles {
                    Generator.sampledFrom(
                        InvokeHighlightFile(it),
                        InvokeFindUsages(it)
                    )
                })
    }

    private val allowedFileExtensions =
        setOf(
            KotlinFileType.EXTENSION,
            //JavaFileType.DEFAULT_EXTENSION,
            PropertiesFileType.DEFAULT_EXTENSION,
            "vm"
        )

    private fun doActionsOnFiles(
        rootPath: String = targetPath(),
        fileFilter: FileFilter = FileFilter { it.extension in allowedFileExtensions },
        actions: Function<PsiFile, Generator<out ImperativeCommand>>
    ): Supplier<ImperativeCommand> {
        val randomFiles = MadTestingUtil.randomFiles(rootPath, fileFilter)

        val psiManager = PsiManager.getInstance(myProject)
        val virtualFileManager = VirtualFileManager.getInstance()

        val action = { env: ImperativeCommand.Environment, psiFile: PsiFile ->
            env.executeCommands(Generator.from { data: GenerationEnvironment ->
                data.generate(actions.apply(psiFile))
            })
        }

        return Supplier<ImperativeCommand> {
            MadTestingAction { env: ImperativeCommand.Environment ->
                RunAll(
                    ThrowableRunnable {
                        val ioFile =
                            env.generateValue(randomFiles, "Working with %s")
                        val vFile =
                            virtualFileManager.findFileByNioPath(ioFile.toPath()) ?: error("unable to lookup $ioFile")

                        val psiFile: PsiFile = psiManager.findFile(vFile) ?: error("unable to lookup psi file for $vFile")
                        check(!(psiFile is PsiBinaryFile || psiFile is PsiPlainTextFile)) {
                            "Can't check $vFile due to incorrect file type: $psiFile of ${psiFile.javaClass}"
                        }
                        action(env, psiFile)
                    },
                    //ThrowableRunnable { psiDocumentManager.commitAllDocuments() },
                    ThrowableRunnable { UIUtil.dispatchAllInvocationEvents() }
                ).run()
            }
        }
    }

    private fun targetPath(): String {
        val parent = testDataPath
        // try to look up for Kotlin plugin sources within a test project
        val path = File(parent, "plugins/kotlin").takeIf(File::exists) 
            ?: File(parent, "community/plugins/kotlin").takeIf(File::exists)
                // otherwise use entire project
            ?: File(parent)
        return path.absolutePath
    }

}