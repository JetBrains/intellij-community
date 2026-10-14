// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import org.jetbrains.plugins.gradle.importing.BuildViewMessagesImportingTestCase
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createParentDirectories
import kotlin.io.path.outputStream

/**
 * Imports a Gradle project with IntelliJ Platform dependencies from local Maven and Ivy repositories.
 *
 * The repositories hold the IntelliJ Platform sources (`com.jetbrains.intellij.idea:ideaIC`),
 * the `localIde:IC` component in the way the IntelliJ Platform Gradle Plugin publishes it,
 * and the sources of the third-party libraries bundled with the IntelliJ Platform.
 */
abstract class IntelliJPlatformSourcesImportingTestCase : BuildViewMessagesImportingTestCase() {

  protected companion object {
    const val VERSION = "2024.1"
    const val BUILD_NUMBER = "241.14494.240"
    const val PHPSTORM = "com.jetbrains.intellij.phpstorm:phpstorm:$VERSION"
    const val WEBSTORM = "com.jetbrains.intellij.webstorm:webstorm:$VERSION"
    const val SOURCES_JAR = "ideaIC-$VERSION-sources.jar"

    const val LOCAL_IDE = "localIde:IC:$VERSION"

    /**
     * The test libraries use a group that does not exist in a public repository.
     * A build agent can hold the real library in a read-only Gradle dependency cache.
     * Gradle prefers that cache to the test repositories, and the import then reads a file outside of the allowed roots.
     */
    const val COMMONS_CLI_GROUP = "org.jetbrains.test.commons-cli"
    const val COMMONS_CLI_VERSION = "1.11.0"
    const val COMMONS_CLI_SOURCES_JAR = "commons-cli-$COMMONS_CLI_VERSION-sources.jar"
    val COMMONS_CLI_METADATA = pomProperties(COMMONS_CLI_GROUP, "commons-cli", COMMONS_CLI_VERSION)
    const val OLD_COMMONS_CLI_VERSION = "1.9.0"
    const val OLD_COMMONS_CLI_SOURCES_JAR = "commons-cli-$OLD_COMMONS_CLI_VERSION-sources.jar"
    val OLD_COMMONS_CLI_METADATA = pomProperties(COMMONS_CLI_GROUP, "commons-cli", OLD_COMMONS_CLI_VERSION)

    const val SLF4J_GROUP = "org.jetbrains.test.slf4j"
    const val SLF4J_VERSION = "2.0.9"
    const val SLF4J_SOURCES_JAR = "slf4j-api-$SLF4J_VERSION-sources.jar"
    val SLF4J_METADATA = pomProperties(SLF4J_GROUP, "slf4j-api", SLF4J_VERSION)

    /**
     * Returns the `META-INF/maven/[groupId]/[artifactId]/pom.properties` jar entry of a library.
     */
    fun pomProperties(groupId: String, artifactId: String, version: String): Pair<String, ByteArray> =
      "META-INF/maven/$groupId/$artifactId/pom.properties" to "groupId=$groupId\nartifactId=$artifactId\nversion=$version\n".toByteArray()

    /**
     * Returns a jar entry with a source file of the class [internalName], such as `org/apache/commons/cli/Option`.
     */
    fun sourceFile(internalName: String): Pair<String, ByteArray> {
      val packageName = internalName.substringBeforeLast('/').replace('/', '.')
      val className = internalName.substringAfterLast('/')
      return "$internalName.java" to "package $packageName; public class $className {}".toByteArray()
    }

    /**
     * Returns a jar entry with a minimal valid class file of the class [internalName], such as `org/apache/commons/cli/Option`.
     * The class file indexer must parse the class, so an empty entry is not enough.
     */
    fun classFile(internalName: String): Pair<String, ByteArray> {
      val bytes = ByteArrayOutputStream()
      DataOutputStream(bytes).use { out ->
        out.writeInt(0xCAFEBABE.toInt())
        out.writeShort(0) // minor version
        out.writeShort(52) // major version, Java 8
        out.writeShort(5) // constant pool count
        out.writeByte(7); out.writeShort(2) // #1 Class #2
        out.writeByte(1); out.writeUTF(internalName) // #2 Utf8
        out.writeByte(7); out.writeShort(4) // #3 Class #4
        out.writeByte(1); out.writeUTF("java/lang/Object") // #4 Utf8
        out.writeShort(0x0021) // ACC_PUBLIC | ACC_SUPER
        out.writeShort(1) // this_class
        out.writeShort(3) // super_class
        out.writeShort(0) // interfaces
        out.writeShort(0) // fields
        out.writeShort(0) // methods
        out.writeShort(0) // attributes
      }
      return "$internalName.class" to bytes.toByteArray()
    }
  }

  override fun setUp() {
    super.setUp()
    // The test application does not load the DevKit content module, so its resolver extension is registered here.
    GradleProjectResolverExtension.EP_NAME.point.registerExtension(DevKitGradleProjectResolverExtension(), testRootDisposable)
  }

  /**
   * Imports a Java project with [dependencies] from the local `repo` Maven repository and the local `ivy` Ivy repository.
   *
   * @param downloadSources Whether the Gradle sync downloads the sources of the dependencies.
   */
  protected fun importProjectWithDependencies(vararg dependencies: String, downloadSources: Boolean = true) {
    createSettingsFile("rootProject.name = 'project'")
    importProject("""
      apply plugin: 'java'
      apply plugin: 'idea'
      idea.module.downloadSources = $downloadSources
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

  /**
   * Returns the project-level or module-level library of the Gradle dependency [libraryName].
   * The Gradle import creates a module-level library for a component with several jars.
   */
  protected fun getLibrary(libraryName: String): Library {
    val name = "Gradle: $libraryName"
    val library = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getLibraryByName(name)
                  ?: ModuleManager.getInstance(myProject).modules
                    .flatMap { module -> ModuleRootManager.getInstance(module).orderEntries.filterIsInstance<LibraryOrderEntry>() }
                    .firstOrNull { entry -> entry.libraryName == name }
                    ?.library
    assertNotNull("The library '$name' must exist", library)
    return library!!
  }

  protected fun getSourceUrls(libraryName: String): List<String> =
    getLibrary(libraryName).getUrls(OrderRootType.SOURCES).toList()

  /**
   * Creates the `localIde:IC:[VERSION]` component in the local Ivy repository, in the way the IntelliJ Platform Gradle Plugin does.
   * Every entry of [jars] is a jar name with its extra entries.
   * The `product-info.json` file next to the jars describes the IntelliJ Platform installation.
   */
  protected fun createLocalIde(vararg jars: Pair<String, List<Pair<String, ByteArray>>>) {
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
    createProjectSubFile("$directory/product-info.json", """
      {
        "name": "IntelliJ IDEA",
        "version": "$VERSION",
        "buildNumber": "$BUILD_NUMBER",
        "productCode": "IC"
      }
    """.trimIndent())
    for ((name, entries) in jars) {
      createJar(Path.of(projectPath, directory, "$name.jar"), name, entries)
    }
  }

  /**
   * Creates a POM and a jar with the [classifier] suffix for `[group]:[artifact]:[version]` in the local Maven repository.
   * The jar contains one source file and the extra [entries].
   */
  protected fun createArtifact(
    group: String,
    artifact: String,
    classifier: String,
    vararg entries: Pair<String, ByteArray>,
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
  private fun createJar(jar: Path, packageName: String, entries: List<Pair<String, ByteArray>>) {
    ZipOutputStream(jar.createParentDirectories().outputStream()).use {
      val fileName = packageName.replace('.', '_').replace('-', '_')
      it.putNextEntry(ZipEntry("$fileName/Api.java"))
      it.write("package $fileName; public class Api {}".toByteArray())
      it.closeEntry()
      for ((name, content) in entries) {
        it.putNextEntry(ZipEntry(name))
        it.write(content)
        it.closeEntry()
      }
    }
  }
}
