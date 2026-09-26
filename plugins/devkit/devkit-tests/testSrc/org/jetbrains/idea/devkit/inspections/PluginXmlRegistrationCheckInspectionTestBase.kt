// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfo.IntentionActionDescriptor
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.util.PathUtil
import java.io.File

/**
 * Checks [PluginXmlRegistrationCheckInspection] on a plugin that consists of three JPS modules.
 * The main module and `additionalModule` belong to one plugin module set. `anotherModule` is a separate plugin module.
 * The registration of a class of `anotherModule` in the descriptor of the main module is a problem, and the quick fix
 * moves the registration to the descriptor of `anotherModule`.
 *
 * A subclass supplies the test data. The descriptors are always XML, and [sourceFileExtension] selects the language of
 * the registered classes.
 */
abstract class PluginXmlRegistrationCheckInspectionTestBase : JavaCodeInsightFixtureTestCase() {

  /** The extension of the test data source files, without the dot. */
  protected abstract val sourceFileExtension: String

  private lateinit var tempDirFixture: TempDirTestFixture
  private lateinit var inspection: PluginXmlRegistrationCheckInspection

  override fun setUp() {
    super.setUp()
    tempDirFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    tempDirFixture.setUp()
    inspection = PluginXmlRegistrationCheckInspection()
    myFixture.enableInspections(inspection, XmlUnresolvedReferenceInspection())
    RecursionManager.assertOnRecursionPrevention(testRootDisposable)
  }

  override fun tearDown() {
    try {
      tempDirFixture.tearDown()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  /**
   * Adds the language runtime that the test data needs to [module].
   * The base class needs no runtime, because Java resolves without one.
   */
  protected open fun configureLanguageRuntime(module: Module) {
  }

  protected fun doTestRegistrationCheck() {
    val anotherModule = addModule("anotherModule", "../anotherModuleDir")
    ModuleRootModificationUtil.addModuleLibrary(anotherModule, libraryUrlOf(AnAction::class.java))
    ModuleRootModificationUtil.addModuleLibrary(anotherModule, libraryUrlOf(LanguageExtensionPoint::class.java))
    val additionalModule = addModule("additionalModule", "../additionalModuleDir")
    // the inspection reads the type of the `EP_NAME` field, so `ExtensionPointName` must resolve
    for (epModule in listOf(anotherModule, additionalModule)) {
      ModuleRootModificationUtil.addModuleLibrary(epModule, libraryUrlOf(ExtensionPointName::class.java))
    }
    ModuleRootModificationUtil.addDependency(module, anotherModule)
    ModuleRootModificationUtil.addDependency(module, additionalModule)
    configureLanguageRuntime(module)

    val moduleSet = PluginXmlRegistrationCheckInspection.PluginModuleSet()
    moduleSet.modules.add(module.name)
    moduleSet.modules.add(additionalModule.name)
    inspection.pluginsModules.add(moduleSet)

    for (name in DEPENDENCY_MODULE_CLASSES) {
      copySourceFile("dependencyModule", name, "../anotherModuleDir")
    }
    copySourceFile("additionalModule", "AdditionalModuleClass", "../additionalModuleDir")
    for (name in MAIN_MODULE_CLASSES) {
      copySourceFile("module", name, "")
    }

    val dependencyModulePlugin = copyAndConfigure("registrationCheck/dependencyModule/DependencyModulePlugin.xml",
                                                  "../anotherModuleDir/META-INF/DependencyModulePlugin.xml")
    val mainModulePlugin = copyAndConfigure("registrationCheck/module/MainModulePlugin.xml",
                                            "META-INF/MainModulePlugin.xml")

    myFixture.allowTreeAccessForAllFiles()

    myFixture.testHighlighting(true, false, false, dependencyModulePlugin)
    myFixture.testHighlighting(true, false, false, mainModulePlugin)
    val highlightInfos = myFixture.doHighlighting(HighlightSeverity.WARNING)
    assertSize(6, highlightInfos)

    for (info in highlightInfos) {
      val quickFixes = quickFixesOf(info)
      assertSize(1, quickFixes)
      myFixture.launchAction(quickFixes[0])
    }

    myFixture.checkResultByFile("../anotherModuleDir/META-INF/DependencyModulePlugin.xml",
                                "registrationCheck/dependencyModule/DependencyModulePlugin_after.xml",
                                true)
    myFixture.checkResultByFile("META-INF/MainModulePlugin.xml",
                                "registrationCheck/module/MainModulePlugin_after.xml",
                                true)
  }

  private fun addModule(name: String, relativeDir: String): Module {
    val newModule = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), name, tempDirFixture.findOrCreateDir(relativeDir))
    ModuleRootManager.getInstance(module).sdk?.let { ModuleRootModificationUtil.setModuleSdk(newModule, it) }
    configureLanguageRuntime(newModule)
    return newModule
  }

  private fun copySourceFile(sourceDir: String, className: String, targetDir: String) {
    val fileName = "$className.$sourceFileExtension"
    val target = if (targetDir.isEmpty()) fileName else "$targetDir/$fileName"
    copyAndConfigure("registrationCheck/$sourceDir/$fileName", target)
  }

  private fun copyAndConfigure(sourcePath: String, targetPath: String): VirtualFile {
    val file = myFixture.copyFileToProject(sourcePath, targetPath)
    myFixture.configureFromExistingVirtualFile(file)
    return file
  }

  private fun libraryUrlOf(aClass: Class<*>): String {
    return VfsUtil.getUrlForLibraryRoot(File(PathUtil.getJarPathForClass(aClass)))
  }

  private fun quickFixesOf(info: HighlightInfo): List<IntentionAction> {
    val result = ArrayList<IntentionAction>()
    info.findRegisteredQuickFix<Any?> { descriptor: IntentionActionDescriptor, _: TextRange? ->
      result.add(descriptor.action)
      null
    }
    return result
  }
}

private val DEPENDENCY_MODULE_CLASSES = listOf(
  "DependencyModuleClass",
  "MyLanguageExtension",
  "MyLanguageExtensionPoint",
  "MyFileTypeExtension",
  "MyFileTypeExtensionPoint",
  "DependencyModuleAction",
  "DependencyModuleClassWithEpName",
  "MyServiceInterface",
  "MyServiceImplementation",
)

private val MAIN_MODULE_CLASSES = listOf(
  "MainModuleClass",
  "MainModuleBeanClass",
  "MyServiceImplementation2",
)
