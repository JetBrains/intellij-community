// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePropertyAccessSyntax")

package com.intellij.util.lang

import com.intellij.util.io.Compressor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URL
import java.nio.file.Path

class PathClassLoaderTest {
  @Test
  fun `sub url resolve`(@TempDir dir: Path) {
    val file = dir.resolve("plugin.jar")
    val compressor = Compressor.Zip(file.toFile())
    compressor.addFile("help/help.html", "<img draggable=\"false\" src=\"screenshot.png\" alt=\"Settings\">".toByteArray(Charsets.UTF_8))
    val expectedData = byteArrayOf(42, 24)
    compressor.addFile("help/screenshot.png", expectedData)
    compressor.close()
    val classPath = ClassPath(listOf(file), UrlClassLoader.Builder(), PathClassLoader.RESOURCE_FILE_FACTORY, true)
    val resource = classPath.findResource("help/help.html")
    assertThat(resource).isNotNull()
    assertThat(URL(resource!!.url, "screenshot.png").content).isEqualTo(expectedData)
  }
}