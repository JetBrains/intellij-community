// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.configurationStore.saveComponentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.vcs.changes.VcsIgnoreManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.VfsTestUtil
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import git4idea.test.GitSingleRepoTest
import org.assertj.core.api.Assertions.assertThat

class RunConfigurationVcsIgnoreTest : GitSingleRepoTest() {
  private val configurationName = "Unnamed"

  override fun isCreateDirectoryBasedProject() = true

  override fun setUpProject() {
    super.setUpProject()
    // create .idea directory
    ApplicationManager.getApplication().invokeAndWait { saveComponentManager(project) }
  }

  override fun setUpModule() {
    myModule = createMainModule()
  }

  fun `test run configuration not ignored`() {
    val gitIgnore = VfsTestUtil.createFile(projectRoot, GITIGNORE)
    gitIgnore.write("!$configurationName")

    val vcsIgnoreManager = VcsIgnoreManager.getInstance(project)
    ApplicationManager.getApplication().invokeAndWait {
      assertFalse(vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName))
      assertFalse(vcsIgnoreManager.isDirectoryVcsIgnored("$projectPath/.idea/runConfigurations"))
    }

    gitIgnore.write("!$configurationName*")

    assertFalse(invokeAndWaitIfNeeded { vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName) })
  }

  fun `test run configuration ignored`() {
    val gitIgnore = VfsTestUtil.createFile(projectRoot, GITIGNORE)
    gitIgnore.write("$configurationName*")
    ApplicationManager.getApplication().invokeAndWait {
      assertThat(VcsIgnoreManager.getInstance(project).isRunConfigurationVcsIgnored(configurationName)).isTrue()
    }
  }

  fun `test remove run configuration from ignore`() {
    val gitIgnore = VfsTestUtil.createFile(projectRoot, GITIGNORE)
    gitIgnore.write(".idea")
    val vcsIgnoreManager = VcsIgnoreManager.getInstance(project)
    ApplicationManager.getApplication().invokeAndWait {
      assertThat(vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName)).isTrue()
      assertThat(vcsIgnoreManager.isDirectoryVcsIgnored("$projectPath/.idea/runConfigurations")).isTrue()

      vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName)
      assertThat(vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName)).isFalse()
    }

    gitIgnore.write(".idea/")

    invokeAndWaitIfNeeded { vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName) }
    assertFalse(invokeAndWaitIfNeeded { vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName) })

    gitIgnore.write(".id*")

    invokeAndWaitIfNeeded { vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName) }
    assertFalse(invokeAndWaitIfNeeded { vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName) })

    gitIgnore.write(".id*/")

    invokeAndWaitIfNeeded { vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName) }
    assertFalse(invokeAndWaitIfNeeded { vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName) })

    gitIgnore.write("*.xml")

    invokeAndWaitIfNeeded { vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName) }
    assertFalse(invokeAndWaitIfNeeded { vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName) })

    gitIgnore.write(".idea/*.xml")

    invokeAndWaitIfNeeded { vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName) }
    assertFalse(invokeAndWaitIfNeeded { vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName) })

    gitIgnore.write("$configurationName.xml")

    invokeAndWaitIfNeeded { vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName) }
    assertFalse(invokeAndWaitIfNeeded { vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName) })
  }
}

private fun VirtualFile.write(data: String) {
  runWriteActionAndWait {
    setBinaryContent(data.toByteArray())
  }
}
