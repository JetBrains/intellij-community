// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util.scenarios

import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.util.step
import com.intellij.testGuiFramework.utils.TestUtilsClass
import com.intellij.testGuiFramework.utils.TestUtilsClassCompanion
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertFalse

/**
 * Set of utils to work with a File as an object on the File System
 * No UI actions are supposed
 * GuiTestCase is used only for logging utils
 * */
class FileSystemUtils(val testCase: GuiTestCase) : TestUtilsClass(testCase) {
  companion object : TestUtilsClassCompanion<FileSystemUtils>(
    { FileSystemUtils(it) }
  )
}

val GuiTestCase.fileSystemUtils by FileSystemUtils

fun FileSystemUtils.checkFileExists(filePath: Path) {
  assertFileExists(filePath)
  assertFileNotEmpty(filePath)
}

fun FileSystemUtils.assertFileExists(filePath: Path) {
  step("check whether file `$filePath` created") {
    assert(filePath.toFile().exists()) { "Can't find a file `$filePath`" }
  }
}

fun FileSystemUtils.checkFileAbsent(filePath: Path) {
  step("check whether file `$filePath` is absent") {
    assertFalse(filePath.toFile().exists(), "File `$filePath` is present")
  }
}

fun FileSystemUtils.assertFileNotEmpty(filePath: Path) {
  step("check whether file `$filePath` is not empty") {
    assert(filePath.toFile().length() > 0) { "File `$filePath` is empty" }
  }
}

fun FileSystemUtils.checkFileContainsLine(filePath: Path, line: String) {
  step("check whether ${filePath.fileName} contains line `$line`") {
    assert(Files.readAllLines(filePath).contains(line)) { "Line `$line` not found" }
  }
}

fun FileSystemUtils.assertProjectPathExists(projectPath: String) {
  step("check whether path `$projectPath` exists") {
    assert(Files.exists(Paths.get(projectPath))) { "Test project $projectPath should be created before test starting" }
  }
}
