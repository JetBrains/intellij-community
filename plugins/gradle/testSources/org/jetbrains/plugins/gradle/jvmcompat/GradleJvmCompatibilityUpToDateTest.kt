// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
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
    val generatedSrcRoot = File(PathManager.getCommunityHomePath(), "plugins/gradle/generated")
    val filesList = generatedSrcRoot.walkTopDown().iterator().asSequence().filter { it.isFile }.filter { it.name != "GradleIcons.java" }.toList()
    TestCase.assertEquals("Should be exactly 1 file in " + generatedSrcRoot.absolutePath, 1, filesList.size)
    val fileInProject = filesList.single()
    val copyrightLine = fileInProject.readLines().first()
    assertTrue(copyrightLine.startsWith("//"))
    generateJvmSupportMatrices(compatibilityJsonData, tempFile, ApplicationInfo.getInstance().fullVersion, copyrightLine)
    UsefulTestCase.assertSameLinesWithFile(fileInProject.absolutePath, tempFile.toFile().readText())
    tempFile.deleteIfExists()
  }
}