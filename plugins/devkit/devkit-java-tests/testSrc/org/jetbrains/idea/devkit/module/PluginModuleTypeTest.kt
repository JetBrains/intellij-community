// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.module

import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.idea.devkit.build.PluginBuildConfiguration
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType

class PluginModuleTypeTest : JavaCodeInsightFixtureTestCase() {

  fun `test plugin xml in directory resource root`() {
    val module = addModule()
    val pluginXml = addDescriptor("META-INF/plugin.xml")
    addResourceRoot(module, pluginXml.parent.parent)

    assertThat(PluginModuleType.getPluginXml(module)?.virtualFile).isEqualTo(pluginXml)
  }

  fun `test plugin xml in META-INF directory resource root`() {
    val module = addModule()
    val pluginXml = addDescriptor("META-INF/plugin.xml")
    addResourceRoot(module, pluginXml.parent, relativeOutputPath = "META-INF")

    assertThat(PluginModuleType.getPluginXml(module)?.virtualFile).isEqualTo(pluginXml)
  }

  fun `test plugin xml in single-file resource root`() {
    val module = addModule()
    val pluginXml = addDescriptor("META-INF/plugin.xml")
    addResourceRoot(module, pluginXml, relativeOutputPath = "META-INF")

    assertThat(PluginModuleType.getPluginXml(module)?.virtualFile).isEqualTo(pluginXml)
  }

  fun `test no plugin xml outside META-INF in output`() {
    val module = addModule()
    val pluginXml = addDescriptor("META-INF/plugin.xml")
    addResourceRoot(module, pluginXml.parent)

    assertThat(PluginModuleType.getPluginXml(module)).isNull()
  }

  fun `test content module descriptor in single-file resource root`() {
    val module = addModule()
    val moduleXml = addDescriptor("com.example.module.xml")
    addResourceRoot(module, moduleXml)

    assertThat(PluginModuleType.getContentModuleDescriptorXml(module)?.virtualFile).isEqualTo(moduleXml)
  }

  fun `test no content module descriptor outside output root`() {
    val module = addModule()
    val moduleXml = addDescriptor("sub/com.example.module.xml")
    addResourceRoot(module, moduleXml, relativeOutputPath = "sub")

    assertThat(PluginModuleType.getContentModuleDescriptorXml(module)).isNull()
  }

  fun `test plugin module type by id`() {
    val pluginModule = addModule("plugin", PluginModuleType())
    val javaModule = addModule("java", JavaModuleType.getModuleType())

    assertThat(pluginModule.moduleTypeName).isEqualTo(PluginModuleType.ID)
    assertThat(PluginModuleType.isOfType(pluginModule)).isTrue()
    assertThat(PluginModuleType.isOfType(javaModule)).isFalse()
    assertThat(PluginBuildConfiguration.getInstance(pluginModule)).isNotNull()
    assertThat(PluginBuildConfiguration.getInstance(javaModule)).isNull()
  }

  fun `test candidate modules are plugin modules depending on the module`() {
    val library = addModule("library", JavaModuleType.getModuleType())
    val pluginModule = addModule("plugin", PluginModuleType())
    val otherJavaModule = addModule("other", JavaModuleType.getModuleType())
    ModuleRootModificationUtil.addDependency(pluginModule, library)
    ModuleRootModificationUtil.addDependency(otherJavaModule, library)
    val pluginXml = myFixture.addFileToProject("plugin/META-INF/plugin.xml", "<idea-plugin/>").virtualFile
    PluginBuildConfiguration.getInstance(pluginModule)!!.setPluginXmlFromVirtualFile(pluginXml)

    assertThat(PluginModuleType.getCandidateModules(library)).containsExactly(pluginModule)
    assertThat(PluginModuleType.isPluginModuleOrDependency(library)).isTrue()
    assertThat(PluginModuleType.isPluginModuleOrDependency(otherJavaModule)).isFalse()
  }

  private fun addModule(): Module {
    return PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "com.example.module", myFixture.tempDirFixture.findOrCreateDir("module"))
  }

  private fun addModule(name: String, type: ModuleType<*>): Module {
    return PsiTestUtil.addModule(project, type, name, myFixture.tempDirFixture.findOrCreateDir(name))
  }

  private fun addDescriptor(path: String): VirtualFile {
    return myFixture.addFileToProject("module/resources/$path", "<idea-plugin/>").virtualFile
  }

  private fun addResourceRoot(module: Module, root: VirtualFile, relativeOutputPath: String = "") {
    PsiTestUtil.addSourceRoot(module, root, JavaResourceRootType.RESOURCE, JavaResourceRootProperties(relativeOutputPath, false))
  }
}
