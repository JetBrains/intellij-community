// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling

import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.gradle.service.execution.GRADLE_TOOLING_EXTENSION_CLASSES
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.PathWalkOption
import kotlin.io.path.walk

@TestApplication
class GradleDaemonClasspathTest {

  private companion object {
    private const val MAGIC: Int = 0xCAFEBABE.toInt()
    private const val TAPI_JAR_NAME = "gradle-api"
  }

  @Test
  fun `test gradle daemon classpath are jre 8 compatible`() {
    val classpath = getDaemonClasspath()
    classpath.forEach { entry ->
      val entryPath = Path.of(entry)
      if (Files.isRegularFile(entryPath)) {
        assertJarContainsOnlyJava8CompatibleFiles(entry)
      }
      else if (Files.isDirectory(entryPath)) {
        assertFolderContainsOnlyJava8CompatibleFiles(entryPath)
      }
    }
  }

  @Test
  fun `test gradle daemon classpath does not contain tooling api`() {
    val classpath = getDaemonClasspath()
    for (path in classpath) {
      assertFalse(path.contains(TAPI_JAR_NAME), "Gradle Tooling API should not be presented in the daemon's classpath!")
    }
  }

  private fun assertJarContainsOnlyJava8CompatibleFiles(jarPath: String) {
    JarFile(jarPath).use { file ->
      assertTrue(
        file.version.feature() <= 8,
        """
        $jarPath contains bytecode of version ${file.version.feature()}.
        The maximum version of the bytecode should be 8 to provide compatibility with older Gradle versions!
        """.trimIndent()
      )
    }
  }

  private fun assertFolderContainsOnlyJava8CompatibleFiles(folder: Path) {
    folder.walk(PathWalkOption.INCLUDE_DIRECTORIES)
      .filter { it.fileName.toString().endsWith(".class") }
      .forEach { pathToClass ->
        DataInputStream(BufferedInputStream(Files.newInputStream(pathToClass), 16)).use { stream ->
          val realMagic = stream.readInt()
          assertEquals(
            MAGIC,
            realMagic,
            "The .class $pathToClass should be a canonical Java class. The magic value $realMagic does not match with the $MAGIC value."
          )
          // minor version is not important
          stream.readUnsignedShort()
          val major = stream.readUnsignedShort()
          assertTrue(52 >= major, "The class $pathToClass should be compiled with the Java target less or equal to Java 8.")
        }
      }
  }

  private fun getDaemonClasspath(): Set<String> {
    val additionalClasses = mutableSetOf<Class<*>>()
    additionalClasses.addAll(GRADLE_TOOLING_EXTENSION_CLASSES)
    GradleProjectResolverExtension.EP_NAME.forEachExtensionSafe {
      additionalClasses.addAll(it.toolingExtensionsClasses)
    }
    return additionalClasses
      .mapNotNull { PathManager.getJarPathForClass(it) }
      .toSet()
  }
}