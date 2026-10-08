// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import org.jetbrains.plugins.gradle.importing.BuildViewMessagesImportingTestCase
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension
import org.junit.Test
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createParentDirectories
import kotlin.io.path.outputStream

/**
 * Tests that the Gradle sync attaches the IntelliJ Platform sources with [com.intellij.devkit.gradle.tooling.IntelliJPlatformAuxiliaryArtifactProvider].
 *
 * The PhpStorm and WebStorm dependencies get the sources of IntelliJ IDEA Community (`com.jetbrains.intellij.idea:ideaIC`).
 * A jar with the Maven metadata of a bundled third-party library gets the sources of that library.
 */
class IntelliJPlatformAuxiliaryArtifactProviderTest : BuildViewMessagesImportingTestCase() {

  private companion object {
    private const val VERSION = "2024.1"
    private const val PHPSTORM = "com.jetbrains.intellij.phpstorm:phpstorm:$VERSION"
    private const val WEBSTORM = "com.jetbrains.intellij.webstorm:webstorm:$VERSION"
    private const val SOURCES_JAR = "ideaIC-$VERSION-sources.jar"

    private const val LOCAL_IDE = "localIde:IC:$VERSION"
    private const val COMMONS_CLI_VERSION = "1.11.0"
    private const val COMMONS_CLI_SOURCES_JAR = "commons-cli-$COMMONS_CLI_VERSION-sources.jar"
    private const val COMMONS_CLI_POM_PROPERTIES = "META-INF/maven/commons-cli/commons-cli/pom.properties"
    private val COMMONS_CLI_METADATA = COMMONS_CLI_POM_PROPERTIES to "groupId=commons-cli\nartifactId=commons-cli\nversion=$COMMONS_CLI_VERSION\n"
    private const val OLD_COMMONS_CLI_VERSION = "1.9.0"
    private const val OLD_COMMONS_CLI_SOURCES_JAR = "commons-cli-$OLD_COMMONS_CLI_VERSION-sources.jar"
    private val OLD_COMMONS_CLI_METADATA = COMMONS_CLI_POM_PROPERTIES to "groupId=commons-cli\nartifactId=commons-cli\nversion=$OLD_COMMONS_CLI_VERSION\n"

    private const val SLF4J_VERSION = "2.0.9"
    private const val SLF4J_SOURCES_JAR = "slf4j-api-$SLF4J_VERSION-sources.jar"
    private val SLF4J_METADATA = "META-INF/maven/org.slf4j/slf4j-api/pom.properties" to "groupId=org.slf4j\nartifactId=slf4j-api\nversion=$SLF4J_VERSION\n"
  }

  override fun setUp() {
    super.setUp()
    // The test application does not load the DevKit content module, so its resolver extension is registered here.
    GradleProjectResolverExtension.EP_NAME.point.registerExtension(DevKitGradleProjectResolverExtension(), testRootDisposable)
  }

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
    createArtifact("commons-cli", "commons-cli", "-sources", version = COMMONS_CLI_VERSION)

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
    createArtifact("commons-cli", "commons-cli", "-sources", version = COMMONS_CLI_VERSION)

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
    createArtifact("commons-cli", "commons-cli", "-sources", version = COMMONS_CLI_VERSION)

    importProjectWithDependencies(module)

    val sourceUrls = getSourceUrls(module)
    assertTrue("The library module must get '$COMMONS_CLI_SOURCES_JAR': $sourceUrls",
               sourceUrls.any { it.endsWith("$COMMONS_CLI_SOURCES_JAR!/") })
  }

  @Test
  fun `test merged platform jar keeps the platform sources when every jar has Maven metadata`() {
    createLocalIde("intellij.libraries.commons.cli" to listOf(COMMONS_CLI_METADATA), "intellij.platform.ide.impl" to listOf(SLF4J_METADATA))
    createArtifact("com.jetbrains.intellij.idea", "ideaIC", "-sources")
    createArtifact("commons-cli", "commons-cli", "-sources", version = COMMONS_CLI_VERSION)
    createArtifact("org.slf4j", "slf4j-api", "-sources", version = SLF4J_VERSION)

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
    createArtifact("commons-cli", "commons-cli", "-sources", version = COMMONS_CLI_VERSION)
    createArtifact("commons-cli", "commons-cli", "-sources", version = OLD_COMMONS_CLI_VERSION)

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

  private fun importProjectWithDependencies(vararg dependencies: String) {
    createSettingsFile("rootProject.name = 'project'")
    importProject("""
      apply plugin: 'java'
      apply plugin: 'idea'
      idea.module.downloadSources = true
      repositories {
        maven { url = uri('repo') }
        ivy {
          url = uri('ivy')
          artifactPattern file('ivy').absolutePath + '/[organisation]/[module]/[revision]/[artifact].[ext]'
          ivyPattern file('ivy').absolutePath + '/[organisation]/[module]/[revision]/ivy.xml'
        }
      }
      dependencies {
        ${dependencies.joinToString("\n") { "implementation '$it'" }}
      }
    """.trimIndent())
  }

  private fun getPhpStormSourceUrls(): List<String> = getSourceUrls(PHPSTORM)

  private fun getSourceUrls(libraryName: String): List<String> {
    val library = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getLibraryByName("Gradle: $libraryName")
    assertNotNull("The library of '$libraryName' must exist", library)
    return library!!.getUrls(OrderRootType.SOURCES).toList()
  }

  /**
   * Returns the sources URLs of every `localIde` library of every module, by the file name of its classes jar.
   * The Gradle import creates one library per jar of the component.
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

  /**
   * Creates the `localIde:IC:[VERSION]` component in the local Ivy repository, in the way the IntelliJ Platform Gradle Plugin does.
   * Every entry of [jars] is a jar name with its extra entries.
   */
  private fun createLocalIde(vararg jars: Pair<String, List<Pair<String, String>>>) {
    val directory = "ivy/localIde/IC/$VERSION"
    val descriptor = buildList {
      add("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
      add("<ivy-module version=\"2.0\">")
      add("  <info organisation=\"localIde\" module=\"IC\" revision=\"$VERSION\"/>")
      add("  <configurations>")
      add("    <conf name=\"default\"/>")
      add("  </configurations>")
      add("  <publications>")
      for ((name, _) in jars) {
        add("    <artifact name=\"$name\" type=\"jar\" ext=\"jar\" conf=\"default\"/>")
      }
      add("  </publications>")
      add("</ivy-module>")
    }
    createProjectSubFile("$directory/ivy.xml", descriptor.joinToString("\n"))
    for ((name, entries) in jars) {
      createJar(Path.of(projectPath, directory, "$name.jar"), name, entries)
    }
  }

  /**
   * Creates a POM and a jar with the [classifier] suffix for `[group]:[artifact]:[version]` in the local Maven repository.
   * The jar contains one source file and the extra [entries].
   */
  private fun createArtifact(
    group: String,
    artifact: String,
    classifier: String,
    vararg entries: Pair<String, String>,
    version: String = VERSION,
  ) {
    val directory = "repo/${group.replace('.', '/')}/$artifact/$version"
    createProjectSubFile("$directory/$artifact-$version.pom", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0">
        <modelVersion>4.0.0</modelVersion>
        <groupId>$group</groupId>
        <artifactId>$artifact</artifactId>
        <version>$version</version>
      </project>
    """.trimIndent())
    createJar(Path.of(projectPath, directory, "$artifact-$version$classifier.jar"), artifact, entries.toList())
  }

  /**
   * Creates a jar in [jar] with one source file in the package [packageName] and the extra [entries].
   */
  private fun createJar(jar: Path, packageName: String, entries: List<Pair<String, String>>) {
    ZipOutputStream(jar.createParentDirectories().outputStream()).use {
      val fileName = packageName.replace('.', '_').replace('-', '_')
      it.putNextEntry(ZipEntry("$fileName/Api.java"))
      it.write("package $fileName; public class Api {}".toByteArray())
      it.closeEntry()
      for ((name, content) in entries) {
        it.putNextEntry(ZipEntry(name))
        it.write(content.toByteArray())
        it.closeEntry()
      }
    }
  }
}
