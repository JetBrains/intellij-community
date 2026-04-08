// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.fileOrDirInProjectFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

@TestApplication
class JUnit5ProjectFixtureTest {

  private companion object {
    val blueprintProjectRoot: Path = PathManager.getHomeDirFor(JUnit5ProjectFixtureTest::class.java)!!
      .resolve("platform/testFramework/junit5/test-resources/projectFixture/blueprintProject")
    val projectFromBlueprint = projectFixture(openAfterCreation = true, blueprintResourcePath = blueprintProjectRoot)
    val mainFileInBlueprintProject = projectFromBlueprint.fileOrDirInProjectFixture("src/Main.java")
    val sharedProject0 = projectFixture()
    val sharedProject1 = projectFixture()
    val openedProject = projectFixture(openAfterCreation = true)

    val tempPath = tempPathFixture()
    val preconfiguredProject = preconfigureExistingProjectFixture(tempPath, "existingProject")
    val existingProject = projectFixture(preconfiguredProject)

    var seenProject: Project? = null
  }

  private val localProject0 = projectFixture()
  private val localProject1 = projectFixture()

  @Test
  fun `open after creation`() {
    assertTrue(openedProject.get().isOpen)
  }

  @Test
  fun `fixture returns same instance`() {
    assertSame(sharedProject0.get(), sharedProject0.get())
    assertSame(sharedProject1.get(), sharedProject1.get())
    assertSame(localProject0.get(), localProject0.get())
    assertSame(localProject1.get(), localProject1.get())
  }

  @Test
  fun `projects are different`() {
    assertNotSame(sharedProject0.get(), sharedProject1.get())
    assertNotSame(localProject0.get(), localProject1.get())
    assertNotSame(localProject0.get(), sharedProject0.get())
    assertNotSame(localProject0.get(), sharedProject1.get())
    assertNotSame(localProject1.get(), sharedProject0.get())
    assertNotSame(localProject1.get(), sharedProject1.get())
  }

  @ParameterizedTest
  @ValueSource(ints = [0, 1])
  fun `shared project is kept between tests`(id: Int) {
    val project = sharedProject0.get()
    assertFalse((project as ProjectEx).isDisposed)
    if (id == 0) {
      seenProject = project
    }
    else {
      assertSame(seenProject, project)
      seenProject = null
    }
  }

  @ParameterizedTest
  @ValueSource(ints = [0, 1])
  fun `local project is recreated between tests`(id: Int) {
    val project = localProject0.get()
    assertFalse((project as ProjectEx).isDisposed)
    if (id == 0) {
      seenProject = project
    }
    else {
      assertNotSame(seenProject, project)
      assertTrue((seenProject as ProjectEx).isDisposed)
      seenProject = null
    }
  }

  @Test
  fun `open existing project`() {
    val project = existingProject.get()
    assertEquals("existingProject", project.name)
    assertTrue(project.isOpen)
  }

  @Test
  fun `copy project blueprint before opening`() {
    val project = projectFromBlueprint.get()
    assertTrue(project.isOpen)
    assertEquals("Main.java", mainFileInBlueprintProject.get().name)
    assertEquals(
      "public class Main {\n}\n",
      Path.of(project.basePath!!).resolve("src/Main.java").readText(),
    )
  }
}

/**
 * Sometimes you need to open an existing project in a test.
 * This fixture simulates copying the project to a temporary directory.
 */
private fun preconfigureExistingProjectFixture(pathFixture: TestFixture<Path>, @Suppress("SameParameterValue") projectName: String): TestFixture<Path> = testFixture {
  val projectRoot = pathFixture.init()
  withContext(Dispatchers.IO) {
    val dotIdea = projectRoot.resolve(Project.DIRECTORY_STORE_FOLDER).createDirectories()
    dotIdea.resolve("$projectName.iml").writeText(
      //language=XML
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <module type="EMPTY_MODULE" version="4">
        <component name="NewModuleRootManager">
          <orderEntry type="sourceFolder" forTests="false" />
        </component>
      </module>
      """.trimIndent()
    )

    dotIdea.resolve("modules.xml").writeText(
      //language=XML
      $$"""
      <?xml version="1.0" encoding="UTF-8"?>
      <project version="4">
        <component name="ProjectModuleManager">
          <modules>
            <module fileurl="file://$PROJECT_DIR$/.idea/$$projectName.iml" filepath="$PROJECT_DIR$/.idea/$$projectName.iml" />
          </modules>
        </component>
      </project>
      """.trimIndent()
    )

    dotIdea.resolve(".name").writeText("existingProject")
  }

  initialized(projectRoot) { }
}
