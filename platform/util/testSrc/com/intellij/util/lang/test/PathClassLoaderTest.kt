// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePropertyAccessSyntax")

package com.intellij.util.lang.test

import com.intellij.util.io.Compressor
import com.intellij.util.lang.ClassPath
import com.intellij.util.lang.PathClassLoader
import com.intellij.util.lang.UrlClassLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.invariantSeparatorsPathString

class PathClassLoaderTest {
  @Test
  fun `sub url resolve`(@TempDir dir: Path) {
    val file = dir.resolve("plugin.jar")
    val expectedData = byteArrayOf(42, 24)
    Compressor.Zip(file.toFile()).use { compressor ->
      compressor.addFile("help/help.html", "<img draggable=\"false\" src=\"screenshot.png\" alt=\"Settings\">".toByteArray(Charsets.UTF_8))
      compressor.addFile("help/screenshot.png", expectedData)
    }
    val classPath = ClassPath(listOf(file), UrlClassLoader.build(), PathClassLoader.getResourceFileFactory(), true)
    val resource = classPath.findResource("help/help.html")
    assertThat(resource).isNotNull()
    assertThat(URL(resource!!.url, "screenshot.png").content).isEqualTo(expectedData)
  }

  @Test
  fun `relative jar path`(@TempDir dir: Path) {
    // Regression test for IDEA-314175.
    val jarAbsolutePath = dir.resolve("lib.jar")
    Compressor.Zip(jarAbsolutePath.toFile()).use { compressor ->
      compressor.addFile("resource.txt", "contents".encodeToByteArray())
    }
    val jarRelativePath = Paths.get("").toAbsolutePath().relativize(jarAbsolutePath)
    val classPath = ClassPath(listOf(jarRelativePath), UrlClassLoader.build(), PathClassLoader.getResourceFileFactory(), true)
    val resource = checkNotNull(classPath.findResource("resource.txt"))
    assertThat(resource.url.toString()).isEqualTo("jar:file:${jarAbsolutePath.invariantSeparatorsPathString}!/resource.txt")
  }
}