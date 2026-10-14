// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import org.junit.Test

/**
 * Tests that the Gradle sync attaches the IntelliJ Platform sources with [com.intellij.devkit.gradle.tooling.IntelliJPlatformAuxiliaryArtifactProvider].
 *
 * The PhpStorm and WebStorm dependencies get the sources of IntelliJ IDEA Community (`com.jetbrains.intellij.idea:ideaIC`).
 * A jar with the Maven metadata of a bundled third-party library gets the sources of that library.
 */
class IntelliJPlatformAuxiliaryArtifactProviderTest : IntelliJPlatformSourcesImportingTestCase() {

  @Test
  fun `test sources are attached during sync`() {
    createArtifact("com.jetbrains.intellij.phpstorm", "phpstorm", "")
    createArtifact("com.jetbrains.intellij.idea", "ideaIC", "-sources")

    importProjectWithDependencies(PHPSTORM)

    val sourceUrls = getPhpStormSourceUrls()
    assertTrue("The sources of '$PHPSTORM' must contain '$SOURCES_JAR': $sourceUrls",
               sourceUrls.any { it.endsWith("$SOURCES_JAR!/") })
  }

  @Test
  fun `test missing sources are reported`() {
    createArtifact("com.jetbrains.intellij.phpstorm", "phpstorm", "")
    createArtifact("com.jetbrains.intellij.webstorm", "webstorm", "")

    importProjectWithDependencies(PHPSTORM, WEBSTORM)

    assertTrue(getPhpStormSourceUrls().none { it.contains(SOURCES_JAR) })
  }

  @Test
  fun `test library sources are attached to the jar with Maven metadata`() {
    createLocalIde("intellij.libraries.commons.cli" to listOf(COMMONS_CLI_METADATA), "app" to emptyList())
    createArtifact("com.jetbrains.intellij.idea", "ideaIC", "-sources")
    createArtifact(COMMONS_CLI_GROUP, "commons-cli", "-sources", version = COMMONS_CLI_VERSION)

    importProjectWithDependencies(LOCAL_IDE)

    // The IDE can merge the jars of one component into one library, so the jar can also carry the IntelliJ Platform sources.
    val sourceUrls = getLocalIdeSourceUrls()
    val librarySourceUrls = sourceUrls.getValue("intellij.libraries.commons.cli.jar")
    assertTrue("The jar with Maven metadata must get '$COMMONS_CLI_SOURCES_JAR': $librarySourceUrls",
               librarySourceUrls.any { it.endsWith("$COMMONS_CLI_SOURCES_JAR!/") })

    val platformSourceUrls = sourceUrls.getValue("app.jar")
    assertTrue("The other jar of the component must keep '$SOURCES_JAR': $platformSourceUrls",
               platformSourceUrls.any { it.endsWith("$SOURCES_JAR!/") })
  }

  @Test
  fun `test single jar component keeps the platform sources`() {
    createArtifact("com.jetbrains.intellij.phpstorm", "phpstorm", "", COMMONS_CLI_METADATA)
    createArtifact("com.jetbrains.intellij.idea", "ideaIC", "-sources")
    createArtifact(COMMONS_CLI_GROUP, "commons-cli", "-sources", version = COMMONS_CLI_VERSION)

    importProjectWithDependencies(PHPSTORM)

    val sourceUrls = getPhpStormSourceUrls()
    assertTrue("The single jar must keep '$SOURCES_JAR': $sourceUrls", sourceUrls.any { it.endsWith("$SOURCES_JAR!/") })
    assertTrue(sourceUrls.none { it.contains(COMMONS_CLI_SOURCES_JAR) })
  }

  @Test
  fun `test library module gets the library sources`() {
    val module = "bundledModule:intellij.libraries.commons.cli:IC-$VERSION"
    createArtifact("bundledModule", "intellij.libraries.commons.cli", "", COMMONS_CLI_METADATA, version = "IC-$VERSION")
    createArtifact("com.jetbrains.intellij.idea", "ideaIC", "-sources")
    createArtifact(COMMONS_CLI_GROUP, "commons-cli", "-sources", version = COMMONS_CLI_VERSION)

    importProjectWithDependencies(module)

    val sourceUrls = getSourceUrls(module)
    assertTrue("The library module must get '$COMMONS_CLI_SOURCES_JAR': $sourceUrls",
               sourceUrls.any { it.endsWith("$COMMONS_CLI_SOURCES_JAR!/") })
  }

  @Test
  fun `test merged platform jar keeps the platform sources when every jar has Maven metadata`() {
    createLocalIde("intellij.libraries.commons.cli" to listOf(COMMONS_CLI_METADATA), "intellij.platform.ide.impl" to listOf(SLF4J_METADATA))
    createArtifact("com.jetbrains.intellij.idea", "ideaIC", "-sources")
    createArtifact(COMMONS_CLI_GROUP, "commons-cli", "-sources", version = COMMONS_CLI_VERSION)
    createArtifact(SLF4J_GROUP, "slf4j-api", "-sources", version = SLF4J_VERSION)

    importProjectWithDependencies(LOCAL_IDE)

    val sourceUrls = getLocalIdeSourceUrls()
    val librarySourceUrls = sourceUrls.getValue("intellij.libraries.commons.cli.jar")
    assertTrue("The library jar must get '$COMMONS_CLI_SOURCES_JAR': $librarySourceUrls",
               librarySourceUrls.any { it.endsWith("$COMMONS_CLI_SOURCES_JAR!/") })

    val platformSourceUrls = sourceUrls.getValue("intellij.platform.ide.impl.jar")
    assertTrue("The merged platform jar must keep '$SOURCES_JAR': $platformSourceUrls",
               platformSourceUrls.any { it.endsWith("$SOURCES_JAR!/") })
    assertTrue("The merged platform jar must not get '$SLF4J_SOURCES_JAR': $platformSourceUrls",
               platformSourceUrls.none { it.contains(SLF4J_SOURCES_JAR) })
  }

  @Test
  fun `test two versions of one library get their own sources`() {
    createLocalIde("app" to emptyList(),
                   "intellij.libraries.commons.cli" to listOf(COMMONS_CLI_METADATA),
                   "intellij.maven" to listOf(OLD_COMMONS_CLI_METADATA))
    createArtifact("com.jetbrains.intellij.idea", "ideaIC", "-sources")
    createArtifact(COMMONS_CLI_GROUP, "commons-cli", "-sources", version = COMMONS_CLI_VERSION)
    createArtifact(COMMONS_CLI_GROUP, "commons-cli", "-sources", version = OLD_COMMONS_CLI_VERSION)

    importProjectWithDependencies(LOCAL_IDE)

    val sourceUrls = getLocalIdeSourceUrls()
    val librarySourceUrls = sourceUrls.getValue("intellij.libraries.commons.cli.jar")
    assertTrue("The library jar must get '$COMMONS_CLI_SOURCES_JAR': $librarySourceUrls",
               librarySourceUrls.any { it.endsWith("$COMMONS_CLI_SOURCES_JAR!/") })

    val mergedSourceUrls = sourceUrls.getValue("intellij.maven.jar")
    assertTrue("The jar with the older library must get '$OLD_COMMONS_CLI_SOURCES_JAR': $mergedSourceUrls",
               mergedSourceUrls.any { it.endsWith("$OLD_COMMONS_CLI_SOURCES_JAR!/") })
  }

  @Test
  fun `test missing library sources are skipped`() {
    createLocalIde("intellij.libraries.commons.cli" to listOf(COMMONS_CLI_METADATA), "app" to emptyList())
    createArtifact("com.jetbrains.intellij.idea", "ideaIC", "-sources")

    importProjectWithDependencies(LOCAL_IDE)

    val sourceUrls = getLocalIdeSourceUrls().getValue("intellij.libraries.commons.cli.jar")
    assertTrue("The jar must fall back to '$SOURCES_JAR': $sourceUrls", sourceUrls.any { it.endsWith("$SOURCES_JAR!/") })
  }

  private fun getPhpStormSourceUrls(): List<String> = getSourceUrls(PHPSTORM)

  /**
   * Returns the sources URLs of the `localIde` library of every module, by the file name of each classes jar.
   * The Gradle import merges the jars of one component into one library, so every jar maps to the sources roots of the whole library.
   */
  private fun getLocalIdeSourceUrls(): Map<String, List<String>> {
    val result = ModuleManager.getInstance(myProject).modules
      .flatMap { module -> ModuleRootManager.getInstance(module).orderEntries.filterIsInstance<LibraryOrderEntry>() }
      .flatMap { entry ->
        entry.getRootUrls(OrderRootType.CLASSES)
          .filter { url -> url.contains("/localIde/") }
          .map { url -> url.removeSuffix("!/").substringAfterLast('/') to entry.getRootUrls(OrderRootType.SOURCES).toList() }
      }
      .toMap()
    assertTrue("The libraries of '$LOCAL_IDE' must exist", result.isNotEmpty())
    return result
  }
}
