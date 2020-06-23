// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project

import com.intellij.ide.CommandLineProcessor
import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.platform.ModuleAttachProcessor
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.checkDefaultProjectAsTemplate
import com.intellij.testFramework.rules.createDeleteAppConfigRule
import com.intellij.util.io.createDirectories
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Path

// terms:
// valid: .idea exists
// clean: .idea doesn't exists
// existing: project directory exists

// with ability to attach - there is some defined ProjectAttachProcessor extension (e.g. WS, PS).
// with inability to attach - there is no any defined ProjectAttachProcessor extension (e.g. IU, IC).

@RunWith(Parameterized::class)
internal class OpenProjectTest(private val opener: Opener) {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun params(): Iterable<Opener> {
      return listOf(
        Opener("OpenFileAction") { OpenFileAction.openExistingDir(it, null)!! },
        Opener("CLI") { CommandLineProcessor.doOpenFileOrProject(it, false).project!! }
      )
    }
  }

  val tempDir = TemporaryDirectory()

  @JvmField
  @Rule
  val disposableRule = DisposableRule()

  @JvmField
  @Rule
  val ruleChain = RuleChain(tempDir, createDeleteAppConfigRule())

  @Test
  fun `open valid existing project dir with ability to attach`() {
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(ModuleAttachProcessor()), disposableRule.disposable)
    val projectDir = tempDir.newPath("project")
    projectDir.resolve(".idea").createDirectories()
    openUsingOpenFileActionAndAssertThatProjectContainsOneModule(projectDir, defaultProjectTemplateShouldBeApplied = false)
  }

  @Test
  fun `open clean existing project dir with ability to attach`() {
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(ModuleAttachProcessor()), disposableRule.disposable)
    val projectDir = tempDir.newPath("project")
    projectDir.createDirectories()
    openUsingOpenFileActionAndAssertThatProjectContainsOneModule(projectDir, defaultProjectTemplateShouldBeApplied = true)
  }

  @Test
  fun `open valid existing project dir with inability to attach`() {
    // Regardless of product (Idea vs PhpStorm), if .idea directory exists, but no modules, we must run configurators to add some module.
    // Maybe not fully clear why it is performed as part of project opening and silently, but it is existing behaviour.
    // So, existing behaviour should be preserved and any changes should be done not as part of task "use unified API to open project", but separately later.
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(), disposableRule.disposable)
    val projectDir = tempDir.newPath("project")
    projectDir.resolve(".idea").createDirectories()
    openUsingOpenFileActionAndAssertThatProjectContainsOneModule(projectDir, defaultProjectTemplateShouldBeApplied = false)
  }

  @Test
  fun `open clean existing project dir with inability to attach`() {
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(), disposableRule.disposable)
    val projectDir = tempDir.newPath("project")
    projectDir.createDirectories()
    openUsingOpenFileActionAndAssertThatProjectContainsOneModule(projectDir, defaultProjectTemplateShouldBeApplied = true)
  }

  private fun openUsingOpenFileActionAndAssertThatProjectContainsOneModule(projectDir: Path, defaultProjectTemplateShouldBeApplied: Boolean) {
    checkDefaultProjectAsTemplate { checkDefaultProjectAsTemplateTask ->
      val project = opener.opener(projectDir)!!
      try {
        assertThatProjectContainsOneModule(project)
        checkDefaultProjectAsTemplateTask(project, defaultProjectTemplateShouldBeApplied)
      }
      finally {
        PlatformTestUtil.forceCloseProjectWithoutSaving(project)
      }
    }
  }
}

internal class Opener(private val name: String, val opener: (Path) -> Project?) {
  override fun toString() = name
}

private fun assertThatProjectContainsOneModule(project: Project) {
  assertThat(ModuleManager.getInstance(project).modules).hasSize(1)
}