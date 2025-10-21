// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    Compressor.Zip(file).use { compressor ->
      compressor.addFile("help/help.html", "<img draggable=\"false\" src=\"screenshot.png\" alt=\"Settings\">".toByteArray(Charsets.UTF_8))
      compressor.addFile("help/screenshot.png", expectedData)
    }
    val classPath = ClassPath(listOf(file), UrlClassLoader.build(), PathClassLoader.getResourceFileFactory(), false)
    val resource = classPath.findResource("help/help.html")
    assertThat(resource).isNotNull()
    assertThat(URL(resource!!.url, "screenshot.png").content).isEqualTo(expectedData)
  }

  @Test
  fun `relative jar path`(@TempDir dir: Path) {
    // Regression test for IDEA-314175.
    val jarAbsolutePath = dir.resolve("lib.jar")
    Compressor.Zip(jarAbsolutePath).use { compressor ->
      compressor.addFile("resource.txt", "contents".encodeToByteArray())
    }
    val jarRelativePath = Paths.get("").toAbsolutePath().relativize(jarAbsolutePath)
    val classPath = ClassPath(listOf(jarRelativePath), UrlClassLoader.build(), PathClassLoader.getResourceFileFactory(), false)
    val resource = checkNotNull(classPath.findResource("resource.txt"))
    assertThat(resource.url.toString()).isEqualTo("jar:file:${jarAbsolutePath.invariantSeparatorsPathString}!/resource.txt")
  }

  @Test
  fun `single jar with manifest`(@TempDir dir: Path) {
    val jarAbsolutePath = dir.resolve("lib-wth-classpath-as-manifest.jar")
    Compressor.Zip(jarAbsolutePath).use { compressor ->
      compressor.addFile("META-INF/MANIFEST.MF", """
          Manifest-Version: 1.0
          Class-Path: file:lib%2b/core.jar file:lib%2b/utils.jar file:lib%2b/plugi
           n.jar
          Created-By: Bazel
        """.trimIndent().toByteArray())
    }

    // Test that the system property functionality works
    try {
      System.setProperty(PathClassLoader.RESET_CLASSPATH_FROM_MANIFEST_PROPERTY, "true")

      val classLoader = PathClassLoader(UrlClassLoader.build().files(listOf(jarAbsolutePath)).parent(null))
      val files = classLoader.getFiles()

      // Should have 3 files now from the manifest classpath
      assertThat(files.size).isEqualTo(3)
      assertThat(files[0].fileName.toString()).isEqualTo("core.jar")
      assertThat(files[0].parent.fileName.toString()).isEqualTo("lib+")
      assertThat(files[1].fileName.toString()).isEqualTo("utils.jar")
      assertThat(files[1].parent.fileName.toString()).isEqualTo("lib+")
      assertThat(files[2].fileName.toString()).isEqualTo("plugin.jar")
      assertThat(files[2].parent.fileName.toString()).isEqualTo("lib+")
    }
    finally {
      System.clearProperty(PathClassLoader.RESET_CLASSPATH_FROM_MANIFEST_PROPERTY)
    }
  }
}
