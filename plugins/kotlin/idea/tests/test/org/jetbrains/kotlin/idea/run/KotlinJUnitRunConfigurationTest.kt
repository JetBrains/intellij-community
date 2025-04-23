// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.run

import com.intellij.execution.PsiLocation
import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.junit.TestInClassConfigurationProducer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.refactoring.RefactoringFactory
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.junit.KotlinJUnitRunConfigurationProducer
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.MockLibraryFacility
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.findFunctionByName
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinJUnitRunConfigurationTest : AbstractRunConfigurationBaseTest() {

    private lateinit var mockLibraryFacility: MockLibraryFacility

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K1

    override fun setUp() {
        super.setUp()
        mockLibraryFacility = MockLibraryFacility(testDataDirectory.resolve("mock"))
        mockLibraryFacility.setUp(module)
    }

    override fun tearDown() {
        runAll(
            { (RunManager.Companion.getInstance(myProject) as RunManagerImpl).clearAll() },
            { mockLibraryFacility.tearDown(module) },
            { super.tearDown() },
        )
    }

    fun testSimple() {
        configureProject()
        val configuredModule = configuredModules.single()

        val testDir = configuredModule.testDir!!

        val javaFile = testDir.findChild("MyJavaTest.java")!!
        val kotlinFile = testDir.findChild("MyKotlinTest.kt")!!

        val javaClassConfiguration = getConfiguration(javaFile, project, "MyTest")
        assert(javaClassConfiguration.isProducedBy(TestInClassConfigurationProducer::class.java))
        assert(javaClassConfiguration.configuration.name == "MyJavaTest")

        val javaMethodConfiguration = getConfiguration(javaFile, project, "testA")
        assert(javaMethodConfiguration.isProducedBy(TestInClassConfigurationProducer::class.java))
        assert(javaMethodConfiguration.configuration.name == "MyJavaTest.testA")

        val kotlinClassConfiguration = getConfiguration(kotlinFile, project, "MyKotlinTest")
        assert(kotlinClassConfiguration.isProducedBy(KotlinJUnitRunConfigurationProducer::class.java))
        assert(kotlinClassConfiguration.configuration.name == "MyKotlinTest")

        val kotlinFunctionConfiguration = getConfiguration(kotlinFile, project, "testA")
        assert(kotlinFunctionConfiguration.isProducedBy(KotlinJUnitRunConfigurationProducer::class.java))
        assert(kotlinFunctionConfiguration.configuration.name == "MyKotlinTest.testA")
       
    }

    fun testRenameKotlinClass() {
        configureProject(testDirectory = "Simple")
        val configuredModule = configuredModules.single()

        val testDir = configuredModule.testDir!!

        val kotlinFile = testDir.findChild("MyKotlinTest.kt")!!

        val manager = RunManager.Companion.getInstance(myProject) as RunManagerImpl

        val kotlinClassConfiguration = getConfiguration(kotlinFile, project, "MyKotlinTest")
        assert(kotlinClassConfiguration.configuration is JUnitConfiguration)
        manager.setTemporaryConfiguration(RunnerAndConfigurationSettingsImpl(manager, kotlinClassConfiguration.configuration))

        val kotlinFunctionConfiguration = getConfiguration(kotlinFile, project, "testA")
        assert(kotlinFunctionConfiguration.configuration is JUnitConfiguration)
        manager.setTemporaryConfiguration(RunnerAndConfigurationSettingsImpl(manager, kotlinFunctionConfiguration.configuration))

        val obj = KotlinFullClassNameIndex.get("MyKotlinTest", project, project.allScope()).single()
        val rename = RefactoringFactory.getInstance(project).createRename(obj, "MyBarKotlinTest")
        rename.run()

        assert((kotlinClassConfiguration.configuration as JUnitConfiguration).persistentData.MAIN_CLASS_NAME == "MyBarKotlinTest")
        assert((kotlinFunctionConfiguration.configuration as JUnitConfiguration).persistentData.MAIN_CLASS_NAME == "MyBarKotlinTest")
    
    }
    
    fun testRenameKotlinMethod() {
        configureProject(testDirectory = "Simple")
        val configuredModule = configuredModules.single()

        val testDir = configuredModule.testDir!!

        val kotlinFile = testDir.findChild("MyKotlinTest.kt")!!

        val manager = RunManager.Companion.getInstance(myProject) as RunManagerImpl
        
        val kotlinFunctionConfiguration = getConfiguration(kotlinFile, project, "testA")
        assert(kotlinFunctionConfiguration.configuration is JUnitConfiguration)
        assert((kotlinFunctionConfiguration.configuration as JUnitConfiguration).persistentData.TEST_OBJECT == JUnitConfiguration.TEST_METHOD)
        manager.setTemporaryConfiguration(RunnerAndConfigurationSettingsImpl(manager, kotlinFunctionConfiguration.configuration))

        val obj: KtClassOrObject = KotlinFullClassNameIndex.get("MyKotlinTest", project, project.allScope()).single()
        val method = (obj as KtClass).findFunctionByName("testA")
        assert(method != null)
        val rename = RefactoringFactory.getInstance(project).createRename(method!!, "testA1")
        rename.run()

        assert((kotlinFunctionConfiguration.configuration as JUnitConfiguration).persistentData.METHOD_NAME == "testA1")
    
    }

    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve("runConfigurations/junit")
}

fun getConfiguration(file: VirtualFile, project: Project, pattern: String): ConfigurationFromContext {
    val location: PsiLocation<PsiElement?> =
        when {
          file.isFile -> {
              val psiFile = PsiManager.getInstance(project).findFile(file) ?: error("PsiFile not found for $file")
              val offset = psiFile.text.indexOf(pattern)
              val psiElement = psiFile.findElementAt(offset)
              PsiLocation(psiElement)
          }
          file.isDirectory -> {
              val directory = PsiDirectoryFactory.getInstance(project).createDirectory(file)
              PsiLocation(directory)
          }
          else -> {
              error("")
          }
        }
    val context = ConfigurationContext.createEmptyContextForLocation(location)
    return context.configurationsFromContext.orEmpty().singleOrNull() ?: error("Configuration not found for pattern $pattern")
}