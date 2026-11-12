// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.testFramework.junit5.eel.params.api.DockerTest
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.EelType
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedClass

@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
@ParameterizedClass
@DockerTest(mandatory = false)
internal class GitExecutableManagerEelDockerTest(private val eelHolder: EelHolder) {

  @Test
  fun `a missing git executable on a remote Eel target is reported as not installed`() {
    assumeTrue(eelHolder.type != EelType.Local)

    val executable = GitExecutable.Eel(eelHolder.eel, eelHolder.eel.fs.getPath("/usr/bin/nonexistent-git"))

    assertThrows(GitNotInstalledException::class.java) {
      GitExecutableManager.getInstance().identifyVersion(null, executable)
    }
  }

  @Test
  fun `a git executable with no resolved path on a remote Eel target is reported as not installed`() {
    assumeTrue(eelHolder.type != EelType.Local)

    val executable = GitExecutable.Eel(eelHolder.eel, "git")

    assertThrows(GitNotInstalledException::class.java) {
      GitExecutableManager.getInstance().identifyVersion(null, executable)
    }
  }

  @Test
  fun `executable resolution for the default project still detects the Eel target from the directory`() {
    assumeTrue(eelHolder.type != EelType.Local)

    val defaultProject = ProjectManager.getInstance().defaultProject
    val gitDirectory = eelHolder.eel.fs.getPath("/").asNioPath()

    val executable = GitExecutableManager.getInstance().getExecutable(defaultProject, gitDirectory)

    assertInstanceOf(GitExecutable.Eel::class.java, executable)
  }
}
