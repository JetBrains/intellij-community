// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.testFramework.use
import org.jetbrains.plugins.gradle.settings.GradleSettings

class GradleAutoLinkTest : GradleAutoLinkTestCase() {
  fun `test auto-link project`() {
    val projectDirectory = createProjectSubDir("project")
    createProjectSubFile("project/.idea/compiler.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project version="4">
        <component name="CompilerConfiguration">
          <bytecodeTargetLevel target="14" />
        </component>
      </project>
    """.trimIndent())
    createProjectSubFile("project/settings.gradle", "rootProject.name = 'project'")
    openProjectFrom(projectDirectory).use { project ->
      val gradleSettings = GradleSettings.getInstance(project)
      assertEquals(1, gradleSettings.linkedProjectsSettings.size)
    }
  }

  fun `test don't auto-link project with project model`() {
    val projectDirectory = createProjectSubDir("project")
    createProjectSubFile("project/.idea/compiler.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project version="4">
        <component name="CompilerConfiguration">
          <bytecodeTargetLevel target="14" />
        </component>
      </project>
    """.trimIndent())
    createProjectSubFile("project/.idea/modules.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project version="4">
        <component name="ProjectModuleManager">
          <modules>
            <module fileurl="file://${'$'}PROJECT_DIR${'$'}/project.iml" filepath="${'$'}PROJECT_DIR${'$'}/project.iml" />
          </modules>
        </component>
      </project>
    """.trimIndent())
    createProjectSubFile("project/settings.gradle", "rootProject.name = 'project'")
    openProjectFrom(projectDirectory).use { project ->
      val gradleSettings = GradleSettings.getInstance(project)
      assertEquals(0, gradleSettings.linkedProjectsSettings.size)
    }
  }
}