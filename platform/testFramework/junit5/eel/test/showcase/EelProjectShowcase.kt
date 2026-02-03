// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.showcase

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.platform.testFramework.junit5.eel.fixture.eelFixture
import com.intellij.platform.testFramework.junit5.eel.fixture.tempDirFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class EelProjectShowcase {
  val eel = eelFixture(EelPlatform.Linux(EelPlatform.Arch.Unknown))
  val tempDir = eel.tempDirFixture()
  val project = projectFixture(tempDir, openAfterCreation = true)

  @Test
  fun `project is located on eel`() {
    val project = project.get()

    val pathToProjectFile = Path.of(project.projectFilePath!!)

    Assertions.assertNotNull(pathToProjectFile.asEelPath())
    Assertions.assertFalse(EelPathUtils.isPathLocal(pathToProjectFile))
  }

}