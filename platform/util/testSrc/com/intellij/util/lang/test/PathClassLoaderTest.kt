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
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
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

  @Test
  fun `single jar with manifest with relative classpath entries`(@TempDir dir: Path) {
    val jarAbsolutePath = dir.resolve("lib-wth-relative-classpath-entries-as-manifest.jar")
    Compressor.Zip(jarAbsolutePath).use { compressor ->
      compressor.addFile("META-INF/MANIFEST.MF", """
          Manifest-Version: 1.0
          Class-Path: ../my%20lib/core.jar ../../lib%2b/utils.jar ../../libs/plugi
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
      assertThat(files[0].parent.fileName.toString()).isEqualTo("my lib")
      assertThat(files[0].parent.parent.fileName.toString()).isEqualTo(jarAbsolutePath.parent.parent.fileName.toString())
      assertThat(files[1].fileName.toString()).isEqualTo("utils.jar")
      assertThat(files[1].parent.fileName.toString()).isEqualTo("lib+")
      assertThat(files[1].parent.parent.fileName.toString()).isEqualTo(jarAbsolutePath.parent.parent.parent.fileName.toString())
      assertThat(files[2].fileName.toString()).isEqualTo("plugin.jar")
      assertThat(files[2].parent.fileName.toString()).isEqualTo("libs")
      assertThat(files[2].parent.parent.fileName.toString()).isEqualTo(jarAbsolutePath.parent.parent.parent.fileName.toString())
    }
    finally {
      System.clearProperty(PathClassLoader.RESET_CLASSPATH_FROM_MANIFEST_PROPERTY)
    }
  }

  @Test
  fun `protection domain code source is disabled by default and populated when property is enabled`(@TempDir dir: Path) {
    val jarPath = dir.resolve("sample.jar")
    val className = "p.Sample"
    val classBytes = minimalClassBytes("p/Sample")
    val secondClassBytes = minimalClassBytes("p/Sample2")
    Compressor.Zip(jarPath).use { compressor ->
      compressor.addFile("p/Sample.class", classBytes)
      compressor.addFile("p/Sample2.class", secondClassBytes)
    }

    val defaultUrlLoader = UrlClassLoader.build().files(listOf(jarPath)).allowLock(false).parent(null).get()
    assertThat(defaultUrlLoader.loadClass(className).protectionDomain.codeSource?.location).isNull()

    val defaultPathLoader = PathClassLoader(UrlClassLoader.build().files(listOf(jarPath)).allowLock(false).parent(null))
    assertThat(defaultPathLoader.loadClass(className).protectionDomain.codeSource?.location).isNull()

    try {
      System.setProperty(UrlClassLoader.DEFINE_PROTECTION_DOMAIN_PROPERTY, "true")

      val urlLoader = UrlClassLoader.build().files(listOf(jarPath)).allowLock(false).parent(null).get()
      val urlLoaded = urlLoader.loadClass(className)
      assertThat(urlLoaded.protectionDomain.codeSource).isNotNull()
      assertThat(Paths.get(urlLoaded.protectionDomain.codeSource.location.toURI())).isEqualTo(jarPath)

      val pathLoader = PathClassLoader(UrlClassLoader.build().files(listOf(jarPath)).allowLock(false).parent(null))
      val firstLoaded = pathLoader.loadClass(className)
      val secondLoaded = pathLoader.loadClass("p.Sample2")
      assertThat(firstLoaded.protectionDomain.codeSource).isNotNull()
      assertThat(Paths.get(firstLoaded.protectionDomain.codeSource.location.toURI())).isEqualTo(jarPath)
      assertThat(secondLoaded.protectionDomain).isSameAs(firstLoaded.protectionDomain)

      val transformedPathLoader = PathClassLoader(UrlClassLoader.build().files(listOf(jarPath)).allowLock(false).parent(null))
      var transformCalled = false
      transformedPathLoader.setTransformer(object : PathClassLoader.BytecodeTransformer {
        override fun transform(loader: ClassLoader, className: String, classBytes: ByteArray): ByteArray {
          transformCalled = true
          return classBytes.clone()
        }
      })
      val transformedLoaded = transformedPathLoader.loadClass(className)
      assertThat(transformCalled).isTrue()
      assertThat(transformedLoaded.protectionDomain.codeSource).isNotNull()
      assertThat(Paths.get(transformedLoaded.protectionDomain.codeSource.location.toURI())).isEqualTo(jarPath)
    }
    finally {
      System.clearProperty(UrlClassLoader.DEFINE_PROTECTION_DOMAIN_PROPERTY)
    }
  }

  private fun minimalClassBytes(internalName: String): ByteArray {
    val out = ByteArrayOutputStream()
    DataOutputStream(out).use { dos ->
      dos.writeInt(0xCAFEBABE.toInt())
      dos.writeShort(0)
      dos.writeShort(52) // Java 8
      dos.writeShort(5)  // constant pool count: 1..4
      dos.writeByte(7)   // #1 Class -> #2
      dos.writeShort(2)
      dos.writeUTF8Entry(internalName) // #2 Utf8
      dos.writeByte(7)   // #3 Class -> #4
      dos.writeShort(4)
      dos.writeUTF8Entry("java/lang/Object") // #4 Utf8
      dos.writeShort(0x0021) // ACC_PUBLIC | ACC_SUPER
      dos.writeShort(1)      // this_class
      dos.writeShort(3)      // super_class
      dos.writeShort(0)      // interfaces_count
      dos.writeShort(0)      // fields_count
      dos.writeShort(0)      // methods_count
      dos.writeShort(0)      // attributes_count
    }
    return out.toByteArray()
  }

  private fun DataOutputStream.writeUTF8Entry(value: String) {
    writeByte(1)
    writeUTF(value)
  }
}
