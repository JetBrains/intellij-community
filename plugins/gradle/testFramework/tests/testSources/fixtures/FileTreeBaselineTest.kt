// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.tests.fixtures

import com.intellij.gradle.testFramework.fixtures.impl.FileTreeDifference
import com.intellij.gradle.testFramework.fixtures.impl.FileTreeDifference.ChangedFile
import com.intellij.gradle.testFramework.fixtures.impl.FileTreeDifference.ExtraEntry
import com.intellij.gradle.testFramework.fixtures.impl.FileTreeDifference.MissingDirectory
import com.intellij.gradle.testFramework.fixtures.impl.FileTreeDifference.MissingFile
import com.intellij.gradle.testFramework.fixtures.impl.areTreesEqual
import com.intellij.gradle.testFramework.fixtures.impl.findDifferences
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

class FileTreeBaselineTest {

  @TempDir
  lateinit var tempDirectory: Path

  private lateinit var baseline: Path
  private lateinit var workingTree: Path

  @BeforeEach
  fun setUp() {
    baseline = tempDirectory.resolve("baseline").createDirectories()
    workingTree = tempDirectory.resolve("workingTree").createDirectories()
    for (tree in listOf(baseline, workingTree)) {
      tree.createFile("build.gradle.kts", "plugins { java }")
      tree.createFile("src/main/java/Main.java", "class Main {}")
      tree.resolve("src/test/java").createDirectories()
    }
  }

  @Test
  fun `test equal trees`() {
    assertThat(findDifferences()).isEmpty()
    assertThat(areTreesEqual(baseline, workingTree) { false }).isTrue()
  }

  @Test
  fun `test changed file`() {
    workingTree.createFile("build.gradle.kts", "plugins { groovy }")

    assertDifferences(ChangedFile(Path.of("build.gradle.kts")))
    assertThat(areTreesEqual(baseline, workingTree) { false }).isFalse()
  }

  @Test
  fun `test changed file with the same size`() {
    workingTree.createFile("src/main/java/Main.java", "class Mian {}")

    assertDifferences(ChangedFile(Path.of("src/main/java/Main.java")))
  }

  @Test
  fun `test binary file`() {
    baseline.resolve("wrapper.jar").writeBytes(byteArrayOf(0, 1, 2, -1))
    workingTree.resolve("wrapper.jar").writeBytes(byteArrayOf(0, 1, 2, -1))
    assertThat(findDifferences()).isEmpty()

    workingTree.resolve("wrapper.jar").writeBytes(byteArrayOf(0, 1, 2, -2))
    assertDifferences(ChangedFile(Path.of("wrapper.jar")))
  }

  @Test
  fun `test extra file`() {
    workingTree.createFile("src/main/java/Extra.java", "class Extra {}")

    assertDifferences(ExtraEntry(Path.of("src/main/java/Extra.java")))
  }

  @Test
  fun `test extra directory is one difference`() {
    workingTree.createFile("extra/nested/Extra.java", "class Extra {}")

    assertDifferences(ExtraEntry(Path.of("extra")))
  }

  @Test
  fun `test missing file`() {
    baseline.createFile("settings.gradle.kts", "")

    assertDifferences(MissingFile(Path.of("settings.gradle.kts")))
  }

  @Test
  fun `test missing empty directory`() {
    baseline.resolve("src/main/resources").createDirectories()

    assertDifferences(MissingDirectory(Path.of("src/main/resources")))
  }

  @Test
  fun `test missing directory is followed by its entries`() {
    baseline.createFile("module/src/Module.java", "class Module {}")

    assertDifferences(
      MissingDirectory(Path.of("module")),
      MissingDirectory(Path.of("module/src")),
      MissingFile(Path.of("module/src/Module.java")),
    )
  }

  @Test
  fun `test file replaced by directory`() {
    baseline.createFile("entry", "file")
    workingTree.createFile("entry/nested.txt", "nested")

    assertDifferences(
      ExtraEntry(Path.of("entry")),
      MissingFile(Path.of("entry")),
    )
  }

  @Test
  fun `test directory replaced by file`() {
    baseline.createFile("entry/nested.txt", "nested")
    workingTree.createFile("entry", "file")

    assertDifferences(
      ExtraEntry(Path.of("entry")),
      MissingDirectory(Path.of("entry")),
      MissingFile(Path.of("entry/nested.txt")),
    )
  }

  @Test
  fun `test case-only rename`() {
    baseline.createFile("original.txt", "original")
    workingTree.createFile("ORIGINAL.TXT", "original")

    assertDifferences(
      ExtraEntry(Path.of("ORIGINAL.TXT")),
      MissingFile(Path.of("original.txt")),
    )
  }

  @Test
  fun `test excluded path`() {
    workingTree.createFile("build/classes/Main.class", "extra")
    workingTree.createFile("src/main/java/Main.java", "changed")
    baseline.createFile("src/main/java/build/Missing.java", "missing")
    workingTree.createFile("build.gradle.kts", "changed")

    val differences = findDifferences(baseline, workingTree) { it.endsWith("build") || it.endsWith("Main.java") }

    assertThat(differences).containsExactly(ChangedFile(Path.of("build.gradle.kts")))
  }

  private fun findDifferences(): List<FileTreeDifference> {
    return findDifferences(baseline, workingTree) { false }
  }

  private fun assertDifferences(vararg expected: FileTreeDifference) {
    assertThat(findDifferences()).containsExactly(*expected)
  }

  private fun Path.createFile(relativePath: String, text: String) {
    resolve(relativePath).createParentDirectories().writeText(text)
  }
}
