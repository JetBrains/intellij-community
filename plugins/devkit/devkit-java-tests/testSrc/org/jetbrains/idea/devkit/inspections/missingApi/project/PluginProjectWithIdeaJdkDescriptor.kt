// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi.project

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk
import org.jetbrains.idea.devkit.projectRoots.Sandbox
import java.io.File

/**
 * Descriptor of an IDEA plugin project with configured IDEA JDK.
 */
class PluginProjectWithIdeaJdkDescriptor : LightProjectDescriptor() {

  companion object {

    private const val IDEA_SDK_NAME = "IDEA plugin SDK"

    fun disposeIdeaJdk() {
      val ideaSdk = ProjectJdkTable.getInstance().findJdk(IDEA_SDK_NAME) ?: return
      runWriteAction {
        ProjectJdkTable.getInstance().removeJdk(ideaSdk)
      }
    }
  }

  override fun getModuleType(): ModuleType<*> = PluginModuleType.getInstance()

  override fun getSdk(): Sdk =
    runWriteAction {
      val jdkTable = ProjectJdkTable.getInstance()
      val sdkType = IdeaJdk.getInstance()
      val ideaSdk = jdkTable.createSdk(IDEA_SDK_NAME, sdkType)
      val sdkModificator = ideaSdk.sdkModificator

      sdkModificator.setupInternalJdk(ideaSdk, IdeaTestUtil.getMockJdk18())

      sdkModificator.addIdeaJarContainingClassToClassPath(Editor::class.java)
      sdkModificator.addIdeaJarContainingClassToClassPath(BaseState::class.java)

      sdkModificator.commitChanges()
      jdkTable.addJdk(ideaSdk)
      ideaSdk
    }

  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    super.configureModule(module, model, contentEntry)
    val moduleExtension = model.getModuleExtension(LanguageLevelModuleExtension::class.java)
    moduleExtension.languageLevel = LanguageLevel.HIGHEST
  }

  private fun SdkModificator.setupInternalJdk(ideaSdk: Sdk, javaJdk: Sdk) {
    for (javaRoot in javaJdk.rootProvider.getFiles(OrderRootType.CLASSES)) {
      addRoot(javaRoot, OrderRootType.CLASSES)
    }
    val sandboxHome = FileUtil.join(FileUtil.getTempDirectory(), "plugins-sandbox")
    sdkAdditionalData = Sandbox(sandboxHome, javaJdk, ideaSdk)
    versionString = javaJdk.versionString
  }

  private fun SdkModificator.addIdeaJarContainingClassToClassPath(clazz: Class<*>) {
    val jarFile = File(FileUtil.toSystemIndependentName(PathManager.getJarPathForClass(clazz)!!))
    val virtualFile = VfsUtil.findFileByIoFile(jarFile, true)
    addRoot(virtualFile!!, OrderRootType.CLASSES)
  }

}