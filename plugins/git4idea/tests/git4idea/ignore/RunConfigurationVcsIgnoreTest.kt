// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  override fun getProjectDirOrFile() = getProjectDirOrFile(true)

  override fun setUp() {
    super.setUp()
    invokeAndWaitIfNeeded { saveComponentManager(project) } //will create .idea directory
    vcsIgnoreManager = project.service()
    gitIgnore = File("$projectPath/$GITIGNORE")
  }

  override fun setUpModule() {
    myModule = createMainModule()
  }

  fun `test run configuration not ignored`(){
    gitIgnore.writeText("!$configurationName")

    assertFalse(vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName))

    gitIgnore.writeText("!$configurationName*")

    assertFalse(vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName))
  }

  fun `test run configuration ignored`() {
    gitIgnore.writeText("$configurationName*")

    assertTrue(vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName))
  }

  fun `test remove run configuration from ignore`() {
    gitIgnore.writeText(".idea")

    vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName)
    assertFalse(vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName))

    gitIgnore.writeText(".idea/")

    vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName)
    assertFalse(vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName))

    gitIgnore.writeText(".id*")

    vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName)
    assertFalse(vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName))

    gitIgnore.writeText(".id*/")

    vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName)
    assertFalse(vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName))

    gitIgnore.writeText("*.xml")

    vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName)
    assertFalse(vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName))

    gitIgnore.writeText(".idea/*.xml")

    vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName)
    assertFalse(vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName))

    gitIgnore.writeText("$configurationName.xml")

    vcsIgnoreManager.removeRunConfigurationFromVcsIgnore(configurationName)
    assertFalse(vcsIgnoreManager.isRunConfigurationVcsIgnored(configurationName))
  }
}
