// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.deleteIfExists

class GradleJvmCompatibilityUpToDateTest : LightPlatformTestCase() {
  lateinit var pathToCompatibilityJson: Path

  override fun setUp() {
    super.setUp()
    pathToCompatibilityJson = this.javaClass.classLoader.getResource("compatibility/compatibility.json")?.toURI()?.let {
      Paths.get(it)
    } ?: throw RuntimeException("Cannot find compatibility.json");
  }

  fun `test ensures generated file is up to date to compatibility json`() {
    val tempFile = Files.createTempFile(getTestName(true), "");
    val srcRoot = File(PathManager.getHomePath(true));
    val communityRoot = if (File(srcRoot, "community").isDirectory) File(srcRoot, "community") else srcRoot
    val generatedSrcRoot = File(communityRoot, "plugins/gradle/generated")
    val filesList = generatedSrcRoot.walkTopDown().iterator().asSequence().filter { it.isFile }.filter { it.name != "GradleIcons.java" }.toList();
    TestCase.assertEquals("Should be exactly 1 file in " + generatedSrcRoot.absolutePath, 1, filesList.size)
    var copyrightLine = filesList[0].readLines()[0];
    assertTrue(copyrightLine.startsWith("//"))
    generateJvmSupportMatrices(pathToCompatibilityJson, tempFile, ApplicationInfo.getInstance().fullVersion, copyrightLine)
    UsefulTestCase.assertSameLinesWithFile(filesList[0].absolutePath, tempFile.toFile().readText())
    tempFile.deleteIfExists()
  }
}