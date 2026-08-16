// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.tests

import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.HeavyPlatformTestCase.createChildDirectory
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl
import git4idea.config.GitConfigUtil.COMMIT_TEMPLATE
import git4idea.repo.GitCommitTemplateTracker
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.TestFile
import git4idea.test.createRepository
import git4idea.test.file
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
internal class GitCommitTemplateTest {
  private val contextFixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = contextFixture.get()

  @BeforeEach
  fun setUp() {
    // backgroundPostStartupActivity are not started in unit tests
    context.project.service<GitCommitTemplateTracker>().start()
  }

  @AfterEach
  fun tearDown() {
    context.git("config --global --unset commit.template", ignoreNonZeroExitCode = true)
  }

  @Test
  fun `test set commit template`(): Unit = with(context) {
    val repository = createRepository(project, projectPath)
    val templateContent = """
      Some Template

      # comment1
      # comment2
    """.trimIndent()
    setupCommitTemplate(repository, "commit_template.txt", templateContent)

    assertCommitTemplate(repository, templateContent)
  }

  @Test
  fun `test set and change commit template`(): Unit = with(context) {
    val repository = createRepository(project, projectPath)
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

  @Test
  fun `test local commit template override global`(): Unit = with(context) {
    val repository = createRepository(project, projectPath)
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

  @Test
  fun `test commit template in multiple repositories`(): Unit = with(context) {
    val repo1 = createRepository(project, createChildDirectory(projectRoot, "root1").path)
    val repo2 = createRepository(project, createChildDirectory(projectRoot, "root2").path)

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

  @Test
  fun `test commit template specified relative to git dir`(): Unit = with(context) {
    val repository = createRepository(project, projectPath)
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

  @Test
  fun `test not valid commit template specified relative to git dir`(): Unit = with(context) {
    val repository = createRepository(project, projectPath)
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
    assertThat(commitTemplateTracker.exists(repository)).describedAs("Commit template exist for $repository").isFalse()

    git.config(repository, "--local", COMMIT_TEMPLATE, "template.txt/")
    repository.repositoryFiles.configFile.refresh()
    assertThat(commitTemplateTracker.exists(repository)).describedAs("Commit template exist for $repository").isFalse()
  }

  @Test
  fun `test commit template with empty or blank content`(): Unit = with(context) {
    val repository = createRepository(project, projectPath)

    setupCommitTemplate(repository, "template.txt", "", true)
    assertCommitTemplateNotExists(repository)

    setupCommitTemplate(repository, "template2.txt", "  ", true)
    assertCommitTemplateNotExists(repository)

    setupCommitTemplate(repository, "template3.txt",
                        """
                          
                        """.trimIndent(), true)
    assertCommitTemplateNotExists(repository)
  }

  private fun GitPlatformTestContext.setupCommitTemplate(
    repository: GitRepository,
    templateFileName: String,
    templateContent: String,
    local: Boolean = true,
  ): TestFile {
    val commitTemplate = repository
      .file(templateFileName)
      .assertNotExists()
      .create(templateContent)
    val pathToCommitTemplatePath = FileUtil.toSystemIndependentName(commitTemplate.file.path)
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

  private fun GitPlatformTestContext.assertCommitTemplate(repository: GitRepository, expectedTemplateContent: String?) {
    val commitTemplateTracker = project.service<GitCommitTemplateTracker>()
    assertThat(commitTemplateTracker.exists(repository)).describedAs("Commit template doesn't exist for $repository").isTrue()
    assertThat(commitTemplateTracker.getTemplateContent(repository))
      .describedAs("Commit template content doesn't match $repository")
      .isEqualTo(expectedTemplateContent)
  }

  private fun GitPlatformTestContext.assertCommitTemplateNotExists(repository: GitRepository) {
    val commitTemplateTracker = project.service<GitCommitTemplateTracker>()
    assertThat(commitTemplateTracker.exists(repository)).describedAs("Commit template exist for $repository").isFalse()
    assertThat(commitTemplateTracker.getTemplateContent(repository))
      .describedAs("Commit template content doesn't match $repository")
      .isNull()
  }

  private fun File.refresh() {
    LocalFileSystem.getInstance().refreshIoFiles(setOf(this))
    AsyncVfsEventsPostProcessorImpl.waitEventsProcessed()
  }
}
