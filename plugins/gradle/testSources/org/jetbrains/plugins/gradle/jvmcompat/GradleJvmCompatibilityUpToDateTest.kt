// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.LightPlatformTestCase
import java.io.File
import java.nio.file.Files
import kotlin.io.path.deleteIfExists

class GradleJvmCompatibilityUpToDateTest : LightPlatformTestCase() {
  private lateinit var compatibilityJsonData: String

  override fun setUp() {
    super.setUp()
    compatibilityJsonData = this.javaClass.classLoader.getResourceAsStream("compatibility/compatibility.json")?.reader(Charsets.UTF_8)?.use {
      it.readText()
    } ?: throw RuntimeException("Cannot find compatibility.json")
  }

  fun `test ensures generated file is up to date to compatibility json`() {
    val tempFile = Files.createTempFile(getTestName(true), "")
    val gradleJvmSupportFile = File(PathManager.getCommunityHomePath(), "plugins/gradle/generated/GradleJvmSupportDefaultData.kt").also {
      assertTrue("GradleJvmSupportDefaultData.kt should be generated", it.exists())
    }
    val copyrightLine = gradleJvmSupportFile.readLines().first()
    assertTrue(copyrightLine.startsWith("//"))
    generateJvmSupportMatrices(compatibilityJsonData, tempFile, ApplicationInfo.getInstance().fullVersion, copyrightLine)
    assertSameLinesWithFile(gradleJvmSupportFile.absolutePath, tempFile.toFile().readText())
    tempFile.deleteIfExists()
  }
}