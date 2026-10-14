// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class IntelliJPlatformAttachSourcesProviderTest {

  companion object {
    @ClassRule
    @JvmField
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val tempDir = TemporaryDirectory()

  @Test
  fun `read library coordinates`() {
    val jar = createZip("intellij.libraries.commons.cli.jar",
                        "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0",
                        "META-INF/maven/commons-cli/commons-cli/pom.properties" to pomProperties("commons-cli", "commons-cli", "1.11.0"),
                        "org/apache/commons/cli/Option.class" to "")

    assertEquals(listOf("commons-cli:commons-cli:1.11.0"), readLibraryCoordinates(jarRoot(jar)))
  }

  @Test
  fun `read library coordinates of merged jar`() {
    val jar = createZip("intellij.libraries.batik.jar",
                        "META-INF/maven/org.apache.xmlgraphics/batik-anim/pom.properties" to pomProperties("org.apache.xmlgraphics", "batik-anim", "1.17"),
                        "META-INF/maven/org.apache.xmlgraphics/batik-dom/pom.properties" to pomProperties("org.apache.xmlgraphics", "batik-dom", "1.17"),
                        "META-INF/maven/xml-apis/xml-apis/pom.properties" to pomProperties("xml-apis", "xml-apis", "1.4.01"))

    assertSameElements(readLibraryCoordinates(jarRoot(jar)),
                       "org.apache.xmlgraphics:batik-anim:1.17",
                       "org.apache.xmlgraphics:batik-dom:1.17",
                       "xml-apis:xml-apis:1.4.01")
  }

  @Test
  fun `read library coordinates without maven metadata`() {
    val jar = createZip("app.jar",
                        "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0",
                        "META-INF/maven/commons-cli/commons-cli/pom.xml" to "<project/>")

    assertEquals(emptyList<String>(), readLibraryCoordinates(jarRoot(jar)))
    assertEquals(emptyList<String>(), readLibraryCoordinates(null))
  }

  @Test
  fun `contains package sources`() {
    val sources = createZip("commons-cli-1.11.0-sources.jar",
                            "org/apache/commons/cli/Option.java" to "class Option {}",
                            "kotlinx/coroutines/flow/Flow.kt" to "fun interface Flow")

    assertTrue(containsPackageSources(sources, "org/apache/commons/cli/Option.class"))
    assertTrue(containsPackageSources(sources, $$"org/apache/commons/cli/Option$Builder.class"))
    assertTrue(containsPackageSources(sources, "org/apache/commons/cli/Options.class"))
    assertTrue(containsPackageSources(sources, "kotlinx/coroutines/flow/FlowKt.class"))
    assertFalse("A parent package of a source file holds no source file", containsPackageSources(sources, "org/apache/commons/Lang.class"))
    assertFalse(containsPackageSources(sources, "com/intellij/accessibility/AccessibilityConfigurable.class"))
    assertFalse(containsPackageSources(sources, "Main.class"))
    assertFalse(containsPackageSources(sources.resolveSibling("missing.jar"), "org/apache/commons/cli/Option.class"))
  }

  private fun pomProperties(groupId: String, artifactId: String, version: String) =
    "groupId=$groupId\nartifactId=$artifactId\nversion=$version\n"

  private fun createZip(name: String, vararg entries: Pair<String, String>): Path {
    val path = tempDir.newPath().resolve(name)
    Files.createDirectories(path.parent)
    ZipOutputStream(Files.newOutputStream(path)).use { zip ->
      for ((entryName, content) in entries) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(content.toByteArray())
        zip.closeEntry()
      }
    }
    return path
  }

  private fun jarRoot(path: Path): VirtualFile? {
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: return null
    return JarFileSystem.getInstance().getJarRootForLocalFile(file)
  }
}
