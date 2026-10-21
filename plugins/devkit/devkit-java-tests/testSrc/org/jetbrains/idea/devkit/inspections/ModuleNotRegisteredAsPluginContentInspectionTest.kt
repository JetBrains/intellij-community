// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType

class ModuleNotRegisteredAsPluginContentInspectionTest : JavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(ModuleNotRegisteredAsPluginContentInspection::class.java)
  }

  fun `test register as content module when resource root contains META-INF`() {
    val pluginModule = addModule("com.example.plugin", "plugin")
    val pluginXml = addPluginXml()
    PsiTestUtil.addSourceRoot(pluginModule, pluginXml.parent.parent, JavaResourceRootType.RESOURCE)

    registerContentModuleAndCheck(pluginXml)
  }

  fun `test register as content module when resource root is META-INF directory`() {
    val pluginModule = addModule("com.example.plugin", "plugin")
    val pluginXml = addPluginXml()
    addResourceRoot(pluginModule, pluginXml.parent, "META-INF")

    registerContentModuleAndCheck(pluginXml)
  }

  fun `test register as content module when resource root is single plugin xml file`() {
    val pluginModule = addModule("com.example.plugin", "plugin")
    val pluginXml = addPluginXml()
    addResourceRoot(pluginModule, pluginXml, "META-INF")

    registerContentModuleAndCheck(pluginXml)
  }

  fun `test register as content module when another plugin xml of the module has no id`() {
    val pluginModule = addModule("com.example.plugin", "plugin")
    val testPluginXml = myFixture.addFileToProject("plugin/testResources/META-INF/plugin.xml", "<idea-plugin/>").virtualFile
    PsiTestUtil.addSourceRoot(pluginModule, testPluginXml.parent.parent, JavaResourceRootType.TEST_RESOURCE)
    val pluginXml = addPluginXml()
    PsiTestUtil.addSourceRoot(pluginModule, pluginXml.parent.parent, JavaResourceRootType.RESOURCE)

    registerContentModuleAndCheck(pluginXml)
  }

  fun `test no fix when META-INF directory resource root has no relative output path`() {
    val pluginModule = addModule("com.example.plugin", "plugin")
    addResourceRoot(pluginModule, addPluginXml().parent, "")

    checkNoFix()
  }

  fun `test no fix when plugin xml is not at META-INF in output`() {
    val pluginModule = addModule("com.example.plugin", "plugin")
    addResourceRoot(pluginModule, addPluginXml().parent, "foo/META-INF")

    checkNoFix()
  }

  fun `test no fix when single-file resource root is not plugin xml in META-INF`() {
    val pluginModule = addModule("com.example.plugin", "plugin")
    val pluginXml = myFixture.addFileToProject(
      "plugin/resources/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin</id>
      </idea-plugin>
      """.trimIndent()).virtualFile
    PsiTestUtil.addSourceRoot(pluginModule, pluginXml, JavaResourceRootType.RESOURCE)

    checkNoFix()
  }

  private fun checkNoFix() {
    configureContentModuleXml()
    assertThat(myFixture.doHighlighting()).anyMatch {
      it.description == "The 'com.example.module' module is not declared as content in any plugin descriptor"
    }
    assertThat(myFixture.filterAvailableIntentions(FIX_TEXT)).isEmpty()
  }

  private fun addModule(name: String, path: String): Module {
    return PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), name, myFixture.tempDirFixture.findOrCreateDir(path))
  }

  private fun addResourceRoot(module: Module, root: VirtualFile, relativeOutputPath: String) {
    PsiTestUtil.addSourceRoot(module, root, JavaResourceRootType.RESOURCE, JavaResourceRootProperties(relativeOutputPath, false))
  }

  private fun addPluginXml(): VirtualFile {
    return myFixture.addFileToProject(
      "plugin/resources/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin</id>
      </idea-plugin>
      """.trimIndent()).virtualFile
  }

  private fun configureContentModuleXml() {
    val contentModule = addModule("com.example.module", "plugin/module")
    val moduleXml = myFixture.addFileToProject(
      "plugin/module/resources/com.example.module.xml",
      """
      <idea-plugin>
      </idea-plugin>
      """.trimIndent()).virtualFile
    PsiTestUtil.addSourceRoot(contentModule, moduleXml.parent, JavaResourceRootType.RESOURCE)
    myFixture.configureFromExistingVirtualFile(moduleXml)
  }

  private fun registerContentModuleAndCheck(pluginXml: VirtualFile) {
    configureContentModuleXml()
    myFixture.launchAction(myFixture.findSingleIntention(FIX_TEXT))
    myFixture.openFileInEditor(pluginXml)
    myFixture.checkResult(
      """
      <idea-plugin>
        <id>com.example.plugin</id>
          <content>
              <module name="com.example.module"/>
          </content>
      </idea-plugin>
      """.trimIndent())
  }
}

private const val FIX_TEXT = "Register as content module of the 'com.example.plugin' plugin"
