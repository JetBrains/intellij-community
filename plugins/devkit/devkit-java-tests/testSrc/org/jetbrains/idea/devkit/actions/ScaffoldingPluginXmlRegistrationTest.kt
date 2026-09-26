// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions

import com.intellij.devkit.scaffolding.addModuleToEnclosingPluginIfPresentForTests
import com.intellij.devkit.scaffolding.findEnclosingPluginXmlForTests
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
internal class ScaffoldingPluginXmlRegistrationTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val project
    get() = projectModel.project

  @Test
  fun `prefer direct parent plugin xml to sibling content-module owner for backend module`() {
    runBlocking {
      val root = projectModel.baseProjectDir.newVirtualDirectory("plugin-root")
      val directPluginXml = createFile("plugin-root/resources/META-INF/plugin.xml", pluginXmlText())

      createContentModule("plugin-root/existing.content")
      val inferredPluginXml = createPluginModule("owner.plugin", "owner.plugin", pluginXmlText("existing.content"))
      waitUntilIndexed()

      addModuleToEnclosingPluginIfPresentForTests(project, root, "new.content", BACKEND_TEMPLATE_NAME)

      assertThat(loadPsiText(directPluginXml))
        .contains("""name="new.content"""")
        .contains("""required-if-available="intellij.platform.backend"""")
      assertThat(loadPsiText(inferredPluginXml)).doesNotContain("""name="new.content"""")
    }
  }

  @Test
  fun `add frontend module to unique plugin xml inferred from sibling content module`() {
    runBlocking {
      val root = projectModel.baseProjectDir.newVirtualDirectory("content-root")
      createContentModule("content-root/existing.content")
      val ownerPluginXml = createPluginModule("owner.plugin", "owner.plugin", pluginXmlText("existing.content"))
      waitUntilIndexed()
      waitUntilEnclosingPluginXmlIsResolved(root)

      addModuleToEnclosingPluginIfPresentForTests(project, root, "new.content", FRONTEND_TEMPLATE_NAME)

      assertThat(loadPsiText(ownerPluginXml))
        .contains("""name="new.content"""")
        .contains("""required-if-available="intellij.platform.frontend"""")
    }
  }

  @Test
  fun `add shared module to unique plugin xml inferred from sibling content module`() {
    runBlocking {
      val root = projectModel.baseProjectDir.newVirtualDirectory("content-root")
      createContentModule("content-root/existing.content")
      val ownerPluginXml = createPluginModule("owner.plugin", "owner.plugin", pluginXmlText("existing.content"))
      waitUntilIndexed()
      waitUntilEnclosingPluginXmlIsResolved(root)

      addModuleToEnclosingPluginIfPresentForTests(project, root, "new.content", SHARED_TEMPLATE_NAME)

      assertThat(loadPsiText(ownerPluginXml))
        .contains("""name="new.content"""")
        .contains("""loading="required"""")
    }
  }

  @Test
  fun `skip module registration when sibling content module has multiple owners`() {
    runBlocking {
      val root = projectModel.baseProjectDir.newVirtualDirectory("content-root")
      createContentModule("content-root/existing.content")
      val ownerPluginXml1 = createPluginModule("owner.plugin.one", "owner.plugin.one", pluginXmlText("existing.content"))
      val ownerPluginXml2 = createPluginModule("owner.plugin.two", "owner.plugin.two", pluginXmlText("existing.content"))
      waitUntilIndexed()

      addModuleToEnclosingPluginIfPresentForTests(project, root, "new.content", BACKEND_TEMPLATE_NAME)

      assertThat(loadPsiText(ownerPluginXml1)).doesNotContain("""name="new.content"""")
      assertThat(loadPsiText(ownerPluginXml2)).doesNotContain("""name="new.content"""")
    }
  }

  private fun createContentModule(modulePath: String) {
    val moduleName = modulePath.substringAfterLast('/')
    val module = createModuleRoot(moduleName, modulePath)
    addResourcesRoot(module, modulePath)
    createFile("$modulePath/resources/$moduleName.xml", contentModuleXmlText())
  }

  private fun createPluginModule(moduleName: String, modulePath: String, @Language("XML") pluginXmlText: String): VirtualFile {
    val module = createModuleRoot(moduleName, modulePath)
    addResourcesRoot(module, modulePath)
    return createFile("$modulePath/resources/META-INF/plugin.xml", pluginXmlText)
  }

  private fun createModuleRoot(moduleName: String, modulePath: String): Module {
    return PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), moduleName, projectModel.baseProjectDir.newVirtualDirectory(modulePath))
  }

  private fun addResourcesRoot(module: Module, modulePath: String) {
    val resourcesDir = projectModel.baseProjectDir.newVirtualDirectory("$modulePath/resources")
    PsiTestUtil.addSourceRoot(module, resourcesDir, JavaResourceRootType.RESOURCE)
  }

  private fun createFile(relativePath: String, @Language("XML") text: String) =
    projectModel.baseProjectDir.newVirtualFile(relativePath, text.toByteArray())

  private fun waitUntilIndexed() {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  private fun loadPsiText(file: VirtualFile): String {
    return ApplicationManager.getApplication().runReadAction<String> {
      (PsiManager.getInstance(project).findFile(file)?.text ?: VfsUtil.loadText(file))
    }
  }

  private suspend fun waitUntilEnclosingPluginXmlIsResolved(root: VirtualFile) {
    repeat(5) {
      if (findEnclosingPluginXmlForTests(project, root, "new.content") != null) {
        return
      }
      waitUntilIndexed()
      delay(100.milliseconds)
    }
    error("Plugin xml owner for 'new.content' was not indexed in time")
  }

  private fun pluginXmlText(vararg contentModuleNames: String): String {
    val contentBlock = if (contentModuleNames.isEmpty()) {
      ""
    }
    else {
      buildString {
        appendLine("  <content>")
        for (contentModuleName in contentModuleNames) {
          appendLine("""    <module name="$contentModuleName"/>""")
        }
        appendLine("  </content>")
      }
    }
    return """
      <idea-plugin>
      $contentBlock</idea-plugin>
    """.trimIndent()
  }

  private fun contentModuleXmlText(): String {
    return """
      <idea-plugin>
      </idea-plugin>
    """.trimIndent()
  }

  private companion object {
    const val FRONTEND_TEMPLATE_NAME = "frontend"
    const val BACKEND_TEMPLATE_NAME = "backend"
    const val SHARED_TEMPLATE_NAME = "shared"
  }
}
