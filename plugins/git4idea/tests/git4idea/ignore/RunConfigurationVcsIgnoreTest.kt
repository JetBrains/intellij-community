// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.configurationStore.saveComponentManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.changes.VcsIgnoreManager
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import git4idea.test.GitSingleRepoTest
import java.io.File

class RunConfigurationVcsIgnoreTest : GitSingleRepoTest() {
  private lateinit var vcsIgnoreManager: VcsIgnoreManager
  private lateinit var gitIgnore: File

  private val configurationName = "Unnamed"

  override fun isCreateDirectoryBasedProject() = true

  override fun setUp() {
    super.setUp()
    vcsIgnoreManager = project.service()
    gitIgnore = File("$projectPath/$GITIGNORE")
  }

  override fun setUpProject() {
    super.setUpProject()
    invokeAndWaitIfNeeded { saveComponentManager(project) } //will create .idea directory
  }

  override fun setUpModule() {
    myModule = createMainModule()
  }

  fun `test run configuration not ignored`() {
    gitIgnore.writeText("!$configurationName")

    assertFalse(invokeAndWaitIfNeeded { vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName) })
    assertFalse(invokeAndWaitIfNeeded { vcsIgnoreManager.isDirectoryVcsIgnored("$projectPath/.idea/runConfigurations") })

    gitIgnore.writeText("!$configurationName*")

    assertFalse(invokeAndWaitIfNeeded { vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName) })
  }

  fun `test run configuration ignored`() {
    gitIgnore.writeText("$configurationName*")

    assertTrue(invokeAndWaitIfNeeded { vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName) })
  }

  fun `test remove run configuration from ignore`() {
    gitIgnore.writeText(".idea")
    assertTrue(invokeAndWaitIfNeeded { vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName) })
    assertTrue(invokeAndWaitIfNeeded { vcsIgnoreManager.isDirectoryVcsIgnored("$projectPath/.idea/runConfigurations") })

    invokeAndWaitIfNeeded { vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName) }
    assertFalse(invokeAndWaitIfNeeded { vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName) })

    gitIgnore.writeText(".idea/")

    invokeAndWaitIfNeeded { vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName) }
    assertFalse(invokeAndWaitIfNeeded { vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName) })

    gitIgnore.writeText(".id*")

    invokeAndWaitIfNeeded { vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName) }
    assertFalse(invokeAndWaitIfNeeded { vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName) })

    gitIgnore.writeText(".id*/")

    invokeAndWaitIfNeeded { vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName) }
    assertFalse(invokeAndWaitIfNeeded { vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName) })

    gitIgnore.writeText("*.xml")

    invokeAndWaitIfNeeded { vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName) }
    assertFalse(invokeAndWaitIfNeeded { vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName) })

    gitIgnore.writeText(".idea/*.xml")

    invokeAndWaitIfNeeded { vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName) }
    assertFalse(invokeAndWaitIfNeeded { vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName) })

    gitIgnore.writeText("$configurationName.xml")

    invokeAndWaitIfNeeded { vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName) }
    assertFalse(invokeAndWaitIfNeeded { vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName) })
  }
}
