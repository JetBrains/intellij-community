// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ignore

import com.intellij.configurationStore.saveSettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.vcs.changes.VcsIgnoreManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.prepareUnversionedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
internal class RunConfigurationVcsIgnoreTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  private val configurationName = "Unnamed"

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking {
    with(context) {
      // will create .idea directory
      saveSettings(project)
      edtWriteAction {
        ModuleManager.getInstance(project).newModule("$projectPath/main.iml", "EMPTY_MODULE")
      }
    }
  }

  @Test
  fun `test run configuration not ignored`(): Unit = timeoutRunBlocking {
    with(context) {
      val gitIgnore = prepareUnversionedFile(GITIGNORE, "!$configurationName")
      val vcsIgnoreManager = VcsIgnoreManager.getInstance(project)

      withContext(Dispatchers.EDT) {
        assertThat(vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName)).isFalse()
        assertThat(vcsIgnoreManager.isDirectoryVcsIgnored("$projectPath/.idea/runConfigurations")).isFalse()
      }

      gitIgnore.write("!$configurationName*")

      withContext(Dispatchers.EDT) {
        assertThat(vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName)).isFalse()
      }
    }
  }

  @Test
  fun `test run configuration ignored`(): Unit = timeoutRunBlocking {
    with(context) {
      prepareUnversionedFile(GITIGNORE, "$configurationName*")

      withContext(Dispatchers.EDT) {
        assertThat(VcsIgnoreManager.getInstance(project).isRunConfigurationVcsIgnored(configurationName)).isTrue()
      }
    }
  }

  @Test
  fun `test remove run configuration from ignore`(): Unit = timeoutRunBlocking {
    with(context) {
      val gitIgnore = prepareUnversionedFile(GITIGNORE, ".idea")
      val vcsIgnoreManager = VcsIgnoreManager.getInstance(project)

      withContext(Dispatchers.EDT) {
        assertThat(vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName)).isTrue()
        assertThat(vcsIgnoreManager.isDirectoryVcsIgnored("$projectPath/.idea/runConfigurations")).isTrue()

        vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName)
        assertThat(vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName)).isFalse()
      }

      val patterns = listOf(".idea/", ".id*", ".id*/", "*.xml", ".idea/*.xml", "$configurationName.xml")
      for (pattern in patterns) {
        gitIgnore.write(pattern)

        withContext(Dispatchers.EDT) {
          vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName)
          assertThat(vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName))
            .describedAs("The run configuration must not be ignored by the pattern '%s'", pattern)
            .isFalse()
        }
      }
    }
  }
}

private suspend fun VirtualFile.write(data: String) {
  edtWriteAction {
    setBinaryContent(data.toByteArray())
  }
}
