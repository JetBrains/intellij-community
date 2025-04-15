// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.run

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase.addJdk
import org.jetbrains.kotlin.idea.util.projectStructure.sdk
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.io.File

abstract class AbstractRunConfigurationBaseTest : KotlinCodeInsightTestCase(),
                                                  ExpectedPluginModeProvider {

    private companion object {
        const val DEFAULT_MODULE_NAME = "module"
    }

    protected sealed class Platform {
        abstract fun configure(module: Module)
        abstract fun addJdk(testRootDisposable: Disposable)

        class Jvm(private val sdk: Sdk = IdeaTestUtil.getMockJdk18()) : Platform() {
            override fun configure(module: Module) {
                ConfigLibraryUtil.configureSdk(module, sdk)
                ConfigLibraryUtil.configureKotlinRuntime(module)
            }

            override fun addJdk(testRootDisposable: Disposable) {
                addJdk(testRootDisposable) { sdk }
            }
        }

        object JavaScript : Platform() {
            override fun configure(module: Module) {
                ConfigLibraryUtil.configureSdk(module, IdeaTestUtil.getMockJdk18())
                ConfigLibraryUtil.configureKotlinStdlibJs(module)
            }

            override fun addJdk(testRootDisposable: Disposable) {
                addJdk(testRootDisposable, IdeaTestUtil::getMockJdk18)
            }
        }
    }

    protected var configuredModules: List<ConfiguredModule> = emptyList()
        private set

    protected val defaultConfiguredModule: ConfiguredModule
        get() = getConfiguredModule(DEFAULT_MODULE_NAME)

    protected fun getConfiguredModule(name: String): ConfiguredModule {
        for (configuredModule in configuredModules) {
            val matches =
                (configuredModule.module == this.module && name == DEFAULT_MODULE_NAME)
                        || configuredModule.module.name == name

            if (matches) {
                return configuredModule
            }
        }

        error("Configured module with name $name not found")
    }

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

    override fun tearDown() {
        runAll(
            { unconfigureDefaultModule() },
            { unconfigureOtherModules() },
            { configuredModules = emptyList() },
            { super.tearDown() },
        )
    }

    private fun unconfigureOtherModules() {
        val moduleManager = ModuleManager.getInstance(project)

        val otherConfiguredModules = configuredModules.filter { it.module != this.module }
        for (configuredModule in otherConfiguredModules) {
            moduleManager.disposeModule(configuredModule.module)
        }
    }

    private fun unconfigureDefaultModule() {
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.clear()
            model.sdk = module.sdk

            val compilerModuleExtension = model.getModuleExtension(CompilerModuleExtension::class.java)
            compilerModuleExtension.inheritCompilerOutputPath(true)
            compilerModuleExtension.setCompilerOutputPath(null as String?)
            compilerModuleExtension.setCompilerOutputPathForTests(null as String?)
        }
    }

    protected fun configureProject(platform: Platform = Platform.Jvm(), 
                                   testDirectory: String = getTestName(false)) {
        runWriteAction {
            val projectBaseDir = testDataDirectory.resolve(testDirectory)
            val projectDir = PlatformTestUtil.getOrCreateProjectBaseDir(project)

            val projectBaseVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectBaseDir)
                ?: error("Can't find VirtualFile for $projectBaseDir")

            projectBaseVirtualFile.refresh(false, true)

            VfsUtil.copyDirectory(this, projectBaseVirtualFile, projectDir, null)

            platform.addJdk(testRootDisposable)
            configuredModules = configureModules(projectDir, projectBaseDir, platform)
        }
    }

    private fun configureModules(projectDir: VirtualFile, projectBaseDir: File, platform: Platform): List<ConfiguredModule> {
        val outDir = projectDir.createChildDirectory(this, "out")
        val srcOutDir = outDir.createChildDirectory(this, "production")
        val testOutDir = outDir.createChildDirectory(this, "test")

        val configuredModules = mutableListOf<ConfiguredModule>()

        val mainModuleBaseDir = projectBaseDir.resolve("module")
        if (mainModuleBaseDir.exists()) {
            configuredModules += configureModule(module, platform, mainModuleBaseDir, projectDir, srcOutDir, testOutDir)
        }

        val otherModuleNames = projectBaseDir.listFiles { f -> f.name != "module" }.orEmpty()
        for (moduleBaseDir in otherModuleNames) {
            val module = createModule(projectDir, moduleBaseDir.name)
            configuredModules += configureModule(module, platform, moduleBaseDir, projectDir, srcOutDir, testOutDir)
        }

        return configuredModules
    }

    private fun createModule(projectDir: VirtualFile, name: String): Module {
        val moduleDir = projectDir.findFileByRelativePath(name) ?: error("Directory for module $name not found")
        val moduleImlPath = moduleDir.toNioPath().resolve("$name.iml")
        return ModuleManager.getInstance(project).newModule(moduleImlPath, StdModuleTypes.JAVA.id)
    }

    private fun configureModule(
        module: Module,
        platform: Platform,
        moduleBaseDir: File,
        projectDir: VirtualFile,
        srcOutDir: VirtualFile,
        testOutDir: VirtualFile
    ): ConfiguredModule {
        val moduleDir = projectDir.findFileByRelativePath(moduleBaseDir.name) ?: error("Directory for module ${module.name} not found")

        fun addSourceRoot(name: String, isTestSource: Boolean): VirtualFile? {
            val sourceRootDir = moduleDir.findFileByRelativePath(name) ?: return null
            PsiTestUtil.addSourceRoot(module, sourceRootDir, isTestSource)
            return sourceRootDir
        }

        val srcDir = addSourceRoot("src", isTestSource = false)
        val testDir = addSourceRoot("test", isTestSource = true)

        val srcOutputDir = srcOutDir.createChildDirectory(this, moduleBaseDir.name)
        val testOutputDir = testOutDir.createChildDirectory(this, moduleBaseDir.name)

        PsiTestUtil.setCompilerOutputPath(module, srcOutputDir.url, false)
        PsiTestUtil.setCompilerOutputPath(module, testOutputDir.url, true)

        platform.configure(module)
        return ConfiguredModule(module, srcDir, testDir, srcOutputDir, testOutputDir)
    }

    protected class ConfiguredModule(
        val module: Module,
        val srcDir: VirtualFile?,
        val testDir: VirtualFile?,
        val srcOutputDir: VirtualFile,
        val testOutputDir: VirtualFile
    )

    protected fun createConfigurationFromMain(project: Project, mainFqn: String, save: Boolean = false): KotlinRunConfiguration {
        val mainFunction = findMainFunction(project, mainFqn)
        return createConfigurationFromElement(mainFunction, save) as KotlinRunConfiguration
    }

    protected fun findMainFunction(
        project: Project,
        mainFqn: String
    ): KtNamedFunction {
        val scope = project.allScope()
        val mainFunction =
            KotlinTopLevelFunctionFqnNameIndex[mainFqn, project, scope].firstOrNull()
                ?: run {
                    val className = StringUtil.getPackageName(mainFqn)
                    val shortName = StringUtil.getShortName(mainFqn)
                    KotlinFullClassNameIndex[className, project, scope]
                        .flatMap { it.declarations }
                        .filterIsInstance<KtNamedFunction>()
                        .firstOrNull { it.name == shortName }
                } ?: error("unable to look up main function by fqName: $mainFqn")
        return mainFunction
    }
}