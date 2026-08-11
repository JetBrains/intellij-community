// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions

import com.intellij.devkit.scaffolding.addModuleToCommunityProjectStructureIfNeededForTests
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.readText

@TestApplication
internal class ScaffoldingCommunityProjectStructureTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val project
    get() = projectModel.project

  @Test
  fun `add module under community to community modules xml`() {
    runBlocking {
      createFile(
        "community/.idea/modules.xml",
        communityModulesXmlText(
          "$projectDirMacro/platform/util/intellij.platform.util.iml",
          "$projectDirMacro/plugins/devkit/intellij.devkit.core/intellij.devkit.core.iml",
        ),
      )
      val moduleRoot = projectModel.baseProjectDir.newVirtualDirectory("community/plugins/new.module/intellij.new.module.shared")
      createFile("community/plugins/new.module/intellij.new.module.shared/intellij.new.module.shared.iml", moduleImlText())

      addModuleToCommunityProjectStructureIfNeededForTests(project, moduleRoot, "intellij.new.module.shared", SHARED_TEMPLATE_NAME)

      assertThat(loadCommunityModulesXmlText()).isEqualTo(
        communityModulesXmlText(
          "$projectDirMacro/plugins/devkit/intellij.devkit.core/intellij.devkit.core.iml",
          "$projectDirMacro/plugins/new.module/intellij.new.module.shared/intellij.new.module.shared.iml",
          "$projectDirMacro/platform/util/intellij.platform.util.iml",
        ),
      )
    }
  }

  @Test
  fun `skip module outside community when updating community modules xml`() {
    runBlocking {
      val initialModulesXml = communityModulesXmlText("$projectDirMacro/platform/util/intellij.platform.util.iml")
      createFile("community/.idea/modules.xml", initialModulesXml)
      val moduleRoot = projectModel.baseProjectDir.newVirtualDirectory("plugins/outside.community/intellij.outside.community.shared")
      createFile("plugins/outside.community/intellij.outside.community.shared/intellij.outside.community.shared.iml", moduleImlText())

      addModuleToCommunityProjectStructureIfNeededForTests(project, moduleRoot, "intellij.outside.community.shared", SHARED_TEMPLATE_NAME)

      assertThat(loadCommunityModulesXmlText()).isEqualTo(initialModulesXml)
    }
  }

  private fun createFile(relativePath: String, @Language("XML") text: String) =
    projectModel.baseProjectDir.newVirtualFile(relativePath, text.toByteArray())

  private fun loadCommunityModulesXmlText(): String {
    return projectRootPath().resolve("community/.idea/modules.xml").readText()
  }

  private fun projectRootPath(): Path {
    return Path.of(project.basePath ?: error("Project base path is unavailable"))
  }

  @Language("XML")
  private fun communityModulesXmlText(vararg moduleFilePaths: String): String {
    return buildString {
      appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
      appendLine("<project version=\"4\">")
      appendLine("  <component name=\"ProjectModuleManager\">")
      appendLine("    <modules>")
      for (moduleFilePath in moduleFilePaths) {
        appendLine("      <module fileurl=\"file://$moduleFilePath\" filepath=\"$moduleFilePath\" />")
      }
      appendLine("    </modules>")
      appendLine("  </component>")
      append("</project>")
    }
  }

  @Language("XML")
  private fun moduleImlText(): String {
    return "<module />"
  }

  private companion object {
    val projectDirMacro = '$' + "PROJECT_DIR$"
    const val SHARED_TEMPLATE_NAME = "shared"
  }
}
