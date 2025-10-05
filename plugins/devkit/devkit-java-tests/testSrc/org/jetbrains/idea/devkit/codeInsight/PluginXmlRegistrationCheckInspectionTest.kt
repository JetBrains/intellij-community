// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.codeInsight

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfo.IntentionActionDescriptor
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.util.PathUtil
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.PluginXmlRegistrationCheckInspection
import org.jetbrains.idea.devkit.inspections.PluginXmlRegistrationCheckInspection.PluginModuleSet
import java.io.File
import java.io.IOException

class PluginXmlRegistrationCheckInspectionTest : JavaCodeInsightFixtureTestCase() {

  private var myTempDirFixture: TempDirTestFixture? = null
  private var myInspection: PluginXmlRegistrationCheckInspection? = null

  override fun setUp() {
    super.setUp()
    myTempDirFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    myTempDirFixture!!.setUp()
    myInspection = PluginXmlRegistrationCheckInspection()
    myFixture.enableInspections(myInspection, XmlUnresolvedReferenceInspection())
    RecursionManager.assertOnRecursionPrevention(testRootDisposable)
  }

  override fun tearDown() {
    try {
      myTempDirFixture!!.tearDown()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      myTempDirFixture = null
      super.tearDown()
    }
  }

  override fun getBasePath(): String {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/pluginXmlRegistrationCheck"
  }

  @Throws(IOException::class)
  fun testRegistrationCheck() {
    val anotherModule = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "anotherModule",
                                              myTempDirFixture!!.findOrCreateDir("../anotherModuleDir"))
    ModuleRootModificationUtil.addModuleLibrary(anotherModule, VfsUtil.getUrlForLibraryRoot(File(PathUtil.getJarPathForClass(AnAction::class.java))))
    val additionalModule = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "additionalModule",
                                                 myTempDirFixture!!.findOrCreateDir("../additionalModuleDir"))
    ModuleRootModificationUtil.addModuleLibrary(anotherModule, VfsUtil.getUrlForLibraryRoot(File(PathUtil.getJarPathForClass(LanguageExtensionPoint::class.java))))
    ModuleRootModificationUtil.addDependency(module, anotherModule)
    ModuleRootModificationUtil.addDependency(module, additionalModule)
    val moduleSet = PluginModuleSet()
    moduleSet.modules.add(module.name)
    moduleSet.modules.add(additionalModule.name)
    myInspection!!.pluginsModules.add(moduleSet)

    val dependencyModuleClass = myFixture.copyFileToProject("registrationCheck/dependencyModule/DependencyModuleClass.java",
                                                            "../anotherModuleDir/DependencyModuleClass.java")
    val dependencyModuleLanguageExtensionClass = myFixture.copyFileToProject("registrationCheck/dependencyModule/MyLanguageExtension.java",
                                                                             "../anotherModuleDir/MyLanguageExtension.java")
    val dependencyModuleLanguageExtensionPointClass = myFixture.copyFileToProject("registrationCheck/dependencyModule/MyLanguageExtensionPoint.java",
                                                                                  "../anotherModuleDir/MyLanguageExtensionPoint.java")
    val dependencyModuleFileTypeExtensionClass = myFixture.copyFileToProject("registrationCheck/dependencyModule/MyFileTypeExtension.java",
                                                                             "../anotherModuleDir/MyFileTypeExtension.java")
    val dependencyModuleFileTypeExtensionPointClass = myFixture.copyFileToProject("registrationCheck/dependencyModule/MyFileTypeExtensionPoint.java",
                                                                                  "../anotherModuleDir/MyFileTypeExtensionPoint.java")
    val dependencyModuleActionClass = myFixture.copyFileToProject("registrationCheck/dependencyModule/DependencyModuleAction.java",
                                                                  "../anotherModuleDir/DependencyModuleAction.java")
    val dependencyModuleClassWithEp = myFixture.copyFileToProject("registrationCheck/dependencyModule/DependencyModuleClassWithEpName.java",
                                                                  "../anotherModuleDir/DependencyModuleClassWithEpName.java")
    val dependencyModulePlugin = myFixture.copyFileToProject("registrationCheck/dependencyModule/DependencyModulePlugin.xml",
                                                             "../anotherModuleDir/META-INF/DependencyModulePlugin.xml")
    val additionalModuleClass = myFixture.copyFileToProject("registrationCheck/additionalModule/AdditionalModuleClass.java",
                                                            "../additionalModuleDir/AdditionalModuleClass.java")
    val mainModuleClass = myFixture.copyFileToProject("registrationCheck/module/MainModuleClass.java",
                                                      "MainModuleClass.java")
    val mainModuleBeanClass = myFixture.copyFileToProject("registrationCheck/module/MainModuleBeanClass.java",
                                                          "MainModuleBeanClass.java")
    val mainModulePlugin = myFixture.copyFileToProject("registrationCheck/module/MainModulePlugin.xml",
                                                       "META-INF/MainModulePlugin.xml")

    myFixture.configureFromExistingVirtualFile(dependencyModuleClass)
    myFixture.configureFromExistingVirtualFile(dependencyModuleLanguageExtensionClass)
    myFixture.configureFromExistingVirtualFile(dependencyModuleLanguageExtensionPointClass)
    myFixture.configureFromExistingVirtualFile(dependencyModuleFileTypeExtensionClass)
    myFixture.configureFromExistingVirtualFile(dependencyModuleFileTypeExtensionPointClass)
    myFixture.configureFromExistingVirtualFile(dependencyModuleActionClass)
    myFixture.configureFromExistingVirtualFile(dependencyModuleClassWithEp)
    myFixture.configureFromExistingVirtualFile(dependencyModulePlugin)
    myFixture.configureFromExistingVirtualFile(additionalModuleClass)
    myFixture.configureFromExistingVirtualFile(mainModuleClass)
    myFixture.configureFromExistingVirtualFile(mainModuleBeanClass)
    myFixture.configureFromExistingVirtualFile(mainModulePlugin)

    myFixture.allowTreeAccessForAllFiles()

    myFixture.testHighlighting(true, false, false, dependencyModulePlugin)
    myFixture.testHighlighting(true, false, false, mainModulePlugin)
    val highlightInfos = myFixture.doHighlighting(HighlightSeverity.WARNING)
    assertSize(5, highlightInfos)

    for (info in highlightInfos) {
      val ranges = actions(info)
      assertSize(1, ranges)
      val quickFix = ranges[0]
      myFixture.launchAction(quickFix)
    }

    myFixture.checkResultByFile("../anotherModuleDir/META-INF/DependencyModulePlugin.xml",
                                "registrationCheck/dependencyModule/DependencyModulePlugin_after.xml",
                                true)
    myFixture.checkResultByFile("META-INF/MainModulePlugin.xml",
                                "registrationCheck/module/MainModulePlugin_after.xml",
                                true)
  }

  private fun actions(info: HighlightInfo): List<IntentionAction> {
    val result = ArrayList<IntentionAction>()
    info.findRegisteredQuickFix<Any?> { descriptor: IntentionActionDescriptor, range: TextRange? ->
      result.add(descriptor.action)
      null
    }
    return result
  }
}