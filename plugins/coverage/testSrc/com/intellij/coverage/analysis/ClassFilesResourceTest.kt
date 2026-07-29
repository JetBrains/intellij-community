// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

internal class ClassFilesResourceTest {
  @Test
  fun `directory resource iterates filtered class files and loads bytes`(@TempDir outputRoot: Path) {
    createClassFile(outputRoot.resolve("org/demo/Foo.class"), 1)
    createClassFile(outputRoot.resolve("org/demo/Foo\$Nested.class"), 2)
    createClassFile(outputRoot.resolve("org/demo/Bar.class"), 3)
    createClassFile(outputRoot.resolve("org/demo/sub/Foo.class"), 4)
    createClassFile(outputRoot.resolve("org/recursive/Baz.class"), 5)
    createClassFile(outputRoot.resolve("org/recursive/sub/Deep.class"), 6)

    val packages = listOf(
      PackageEntry("org.demo", listOf("Foo")),
      PackageEntry("org.recursive", null),
    )
    val files = LinkedHashMap<String, ByteArray?>()
    ClassFilesLocator.findClassFiles(outputRoot, packages).use { resource ->
      for (classFile in resource) {
        files[classFile.relativePath] = classFile.loadBytes()
      }
    }

    assertEquals(
      setOf("org/demo/Foo.class", "org/demo/Foo\$Nested.class", "org/recursive/Baz.class", "org/recursive/sub/Deep.class"),
      files.keys,
    )
    assertArrayEquals(byteArrayOf(1), files["org/demo/Foo.class"])
    assertArrayEquals(byteArrayOf(2), files["org/demo/Foo\$Nested.class"])
    assertArrayEquals(byteArrayOf(5), files["org/recursive/Baz.class"])
    assertArrayEquals(byteArrayOf(6), files["org/recursive/sub/Deep.class"])
  }

  @Test
  fun `archive resource is single pass and closes byte source`(@TempDir directory: Path) {
    val archive = directory.resolve("classes.jar")
    JarOutputStream(Files.newOutputStream(archive)).use { output ->
      addEntry(output, "org/demo/Foo.class", 1)
      addEntry(output, "org/demo/Foo\$Nested.class", 2)
      addEntry(output, "org/demo/Bar.class", 3)
    }

    val resource = ClassFilesLocator.findClassFiles(archive, listOf(PackageEntry("org.demo", listOf("Foo"))))
    val first = resource.next()
    assertEquals("org/demo/Foo.class", first.relativePath)
    assertArrayEquals(byteArrayOf(1), first.loadBytes())

    val second = resource.next()
    assertEquals("org/demo/Foo\$Nested.class", second.relativePath)
    resource.close()

    assertFalse(resource.hasNext())
    assertNull(second.loadBytes())
  }

  private fun createClassFile(path: Path, content: Int) {
    Files.createDirectories(path.parent)
    Files.write(path, byteArrayOf(content.toByte()))
  }

  private fun addEntry(output: JarOutputStream, name: String, content: Int) {
    output.putNextEntry(JarEntry(name))
    output.write(content)
    output.closeEntry()
  }
}
