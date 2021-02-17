// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.tests

import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import com.intellij.util.WaitFor
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl
import git4idea.config.GitConfigUtil.COMMIT_TEMPLATE
import git4idea.repo.GitCommitTemplateTracker
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTest
import git4idea.test.TestFile
import git4idea.test.file
import git4idea.test.git
import java.io.File

class GitCommitTemplateTest : GitPlatformTest() {

  override fun setUp() {
    super.setUp()

    waitForTemplateTrackerReady()
  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable { git("config --global --unset commit.template", ignoreNonZeroExitCode = true) },
      ThrowableRunnable { super.tearDown() }
    ).run()
  }

  fun `test set commit template`() {
    val repository = createRepository(projectPath)
    val templateContent = """
      Some Template
      
      # comment1
      # comment2
    """.trimIndent()
    setupCommitTemplate(repository, "commit_template.txt", templateContent)

    assertCommitTemplate(repository, templateContent)
  }

  fun `test set and change commit template`() {
    val repository = createRepository(projectPath)
    val templateContent = """
      Local Template
      
      # comment1
      # comment2
    """.trimIndent()
    val template = setupCommitTemplate(repository, "commit_template.txt", templateContent)

    assertCommitTemplate(repository, templateContent)

    val newTemplateContent = """
      New Local Template
      
      # comment3
      # comment4
    """.trimIndent()
    template.write(newTemplateContent)
    template.file.refresh()

    assertCommitTemplate(repository, newTemplateContent)
  }

  fun `test local commit template override global`() {
    val repository = createRepository(projectPath)
    val globalTemplateContent = """
      Global Template
      
      # comment3
      # comment4
    """.trimIndent()
    setupCommitTemplate(repository, "global_commit_template.txt", globalTemplateContent, false)

    assertCommitTemplate(repository, globalTemplateContent)

    val localTemplateContent = """
      Local Template
      
      # comment1
      # comment2
    """.trimIndent()
    setupCommitTemplate(repository, "local_commit_template.txt", localTemplateContent)

    assertCommitTemplate(repository, localTemplateContent)
  }

  fun `test commit template in multiple repositories`() {
    val repo1 = createRepository(createChildDirectory(projectRoot, "root1").path)
    val repo2 = createRepository(createChildDirectory(projectRoot, "root2").path)

    val templateContent1 = """
      Template for first repository
      
      # comment1
      # comment2
    """.trimIndent()

    val templateContent2 = """
      Template for second repository
      
      # comment3
      # comment4
    """.trimIndent()
    setupCommitTemplate(repo1, "template1.txt", templateContent1)
    setupCommitTemplate(repo2, "template2.txt", templateContent2)

    assertCommitTemplate(repo1, templateContent1)
    assertCommitTemplate(repo2, templateContent2)
  }

  fun `test commit template specified relative to git dir`() {
    val repository = createRepository(projectPath)
    val templateContent = """
      Some Template
      
      # comment1
      # comment2
    """.trimIndent()
    repository
      .file("template.txt")
      .assertNotExists()
      .create(templateContent)
    git.config(repository, "--local", COMMIT_TEMPLATE, "template.txt")
    repository.repositoryFiles.configFile.refresh()

    assertCommitTemplate(repository, templateContent)
  }

  fun `test not valid commit template specified relative to git dir`() {
    val repository = createRepository(projectPath)
    val templateContent = """
      Some Template
      
      # comment1
      # comment2
    """.trimIndent()
    repository
      .file("template.txt")
      .assertNotExists()
      .create(templateContent)
    val commitTemplateTracker = project.service<GitCommitTemplateTracker>()

    git.config(repository, "--local", COMMIT_TEMPLATE, "/template.txt")
    repository.repositoryFiles.configFile.refresh()
    assertTrue("Commit template exist for $repository", !commitTemplateTracker.exists(repository))

    git.config(repository, "--local", COMMIT_TEMPLATE, "template.txt/")
    repository.repositoryFiles.configFile.refresh()
    assertTrue("Commit template exist for $repository", !commitTemplateTracker.exists(repository))
  }

  private fun setupCommitTemplate(repository: GitRepository,
                                  templateFileName: String,
                                  templateContent: String,
                                  local: Boolean = true): TestFile {
    val commitTemplate = repository
      .file(templateFileName)
      .assertNotExists()
      .create(templateContent)
    val pathToCommitTemplatePath = commitTemplate.file.let { FileUtil.toSystemIndependentName(it.path) }
    git.config(repository, if (local) "--local" else "--global", COMMIT_TEMPLATE, pathToCommitTemplatePath)
    if (local) {
      repository.repositoryFiles.configFile.refresh()
    }
    else {
      //explicit notify because of IDEA-131645
      project.service<GitCommitTemplateTracker>().notifyConfigChanged(repository)
    }

    return commitTemplate
  }

  private fun assertCommitTemplate(repository: GitRepository, expectedTemplateContent: String) {
    val commitTemplateTracker = project.service<GitCommitTemplateTracker>()
    assertTrue("Commit template doesn't exist for $repository", commitTemplateTracker.exists(repository))
    assertEquals("Commit template content doesn't match $repository", expectedTemplateContent,
                 commitTemplateTracker.getTemplateContent(repository))
  }

  private fun File.refresh() {
    LocalFileSystem.getInstance().refreshIoFiles(setOf(this))
    AsyncVfsEventsPostProcessorImpl.waitEventsProcessed()
  }

  private fun waitForTemplateTrackerReady() {
    object : WaitFor() {
      override fun condition(): Boolean = project.service<GitCommitTemplateTracker>().isStarted()
      override fun assertCompleted(message: String?) {
        if (!condition()) {
          fail(message)
        }
      }
    }.assertCompleted("Failed to wait ${this::class.simpleName}")
  }
}
