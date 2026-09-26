// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.toml.tests.service

import com.intellij.gradle.java.toml.service.GradleTomlVersionCatalogEntrySearcher
import com.intellij.gradle.java.toml.service.TomlCatalogEntry
import com.intellij.openapi.externalSystem.util.runReadAction
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.plugins.gradle.service.resolve.VersionCatalogSection
import org.jetbrains.plugins.gradle.service.resolve.VersionCatalogSection.BUNDLES
import org.jetbrains.plugins.gradle.service.resolve.VersionCatalogSection.LIBRARIES
import org.jetbrains.plugins.gradle.service.resolve.VersionCatalogSection.PLUGINS
import org.jetbrains.plugins.gradle.service.resolve.VersionCatalogSection.VERSIONS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.toml.lang.TomlLanguage
import org.toml.lang.psi.TomlFile

@TestApplication
class GradleTomlVersionCatalogEntrySearcherTest {
  private val entrySearcher by testFixture {
    initialized(GradleTomlVersionCatalogEntrySearcher()) {}
  }
  private val project by projectFixture()

  @Nested
  @DisplayName("Tests for findEntryElement(...) method")
  inner class FindEntryElementTests {

    private fun test(
      givenEntryPath: String,
      versionCatalogText: String,
      expectedToFind: String? = null,
      checker: (PsiElement?) -> Unit = {},
    ) = runReadAction {
      val tomPsiFile = PsiFileFactory.getInstance(project)
        .createFileFromText(TomlLanguage, versionCatalogText) as TomlFile
      val element = entrySearcher.findEntryElement(tomPsiFile, givenEntryPath)

      if (expectedToFind != null) {
        assertNotNull(element) {
          "Expected that $expectedToFind TOML element is found for the given path $givenEntryPath"
        }
        assertEquals(expectedToFind, element.text) {
          "The found element should match the expected text for the given path $givenEntryPath"
        }
      }
      checker(element)
    }

    @Test
    fun `test findEntryElement for a library entry`() = test(
      givenEntryPath = "groovy.core",
      versionCatalogText = """
        plugins.groovy-core = "plugin"
        [libraries]
        groovy-core = "lib"
      """.trimIndent(),
      expectedToFind = "groovy-core = \"lib\""
    )

    @Test
    fun `test findEntryElement for a library entry when libraries is inline table`() = test(
      givenEntryPath = "groovy.core",
      versionCatalogText = """
        libraries = { groovy-core = "lib" }
      """.trimIndent(),
      expectedToFind = "groovy-core = \"lib\""
    )

    @Test
    fun `test findEntryElement for a library entry when libraries table name is a part of alias`() = test(
      givenEntryPath = "groovy.core",
      versionCatalogText = """
        libraries.groovy-core = "lib"
      """.trimIndent(),
      expectedToFind = "libraries.groovy-core = \"lib\""
    )

    @Test
    fun `test findEntryElement for a bundle entry`() = test(
      givenEntryPath = "bundles.bundle",
      versionCatalogText = """
        [bundles]
        bundle = ["lib"]
      """.trimIndent(),
      expectedToFind = "bundle = [\"lib\"]"
    )

    @Test
    fun `test findEntryElement for a bundle entry when bundles is inline table`() = test(
      givenEntryPath = "bundles.bundle.core",
      versionCatalogText = """
        bundles = { bundle-core = ["lib"] }
      """.trimIndent(),
      expectedToFind = "bundle-core = [\"lib\"]"
    )

    @Test
    fun `test findEntryElement for a bundle entry when bundles table name is a part of alias`() = test(
      givenEntryPath = "bundles.bundle.core",
      versionCatalogText = """
        bundles.bundle-core = "lib"
      """.trimIndent(),
      expectedToFind = "bundles.bundle-core = \"lib\""
    )

    @Test
    fun `test findEntryElement for a version entry`() = test(
      givenEntryPath = "versions.my.foo",
      versionCatalogText = """
        [versions]
        my-foo = ["foo"]
        my-foo-bar = ["foo-bar"]
      """.trimIndent(),
      expectedToFind = "my-foo = [\"foo\"]"
    )

    @Test
    fun `test findEntryElement for a plugin entry`() = test(
      givenEntryPath = "plugins.plugin",
      versionCatalogText = """
        [plugins]
        plugin = "plugin"
      """.trimIndent(),
      expectedToFind = "plugin = \"plugin\""
    )

    @Test
    fun `test findEntryElement for a plugin entry when plugins is inline table`() = test(
      givenEntryPath = "plugins.plugin.core",
      versionCatalogText = """
        plugins = { plugin_core = ["plugin"] }
      """.trimIndent(),
      expectedToFind = "plugin_core = [\"plugin\"]"
    )

    @Test
    fun `test findEntryElement for a plugin entry when plugins table name is a part of alias`() = test(
      givenEntryPath = "plugins.plugin.core",
      versionCatalogText = """
        plugins.plugin_core = "plugin"
      """.trimIndent(),
      expectedToFind = "plugins.plugin_core = \"plugin\""
    )

    @Test
    fun `test findEntryElement when a TOML entry name has various delimiters`() = test(
      givenEntryPath = "alias.core.ext",
      versionCatalogText = """
        [libraries]
        alias_core-ext = "aaa"
      """.trimIndent(),
      expectedToFind = "alias_core-ext = \"aaa\""
    )

    @Test
    fun `test findEntryElement when given entry path has various delimiters`() = test(
      givenEntryPath = "alias_core-ext",
      versionCatalogText = """
        [libraries]
        alias-core-ext = "aaa"
      """.trimIndent(),
      expectedToFind = "alias-core-ext = \"aaa\""
    )

    @Test
    fun `test findEntryElement shouldn't return an entry with name containing given path but larger`() = test(
      givenEntryPath = "alias.core",
      versionCatalogText = """
        [libraries]
        alias-core-ext = "aaa"
      """.trimIndent()
    ) { foundElement ->
      assertNull(foundElement) { "findEntryElement shouldn return an entry having more letters than given path." }
    }

    @Test
    fun `test findEntryElement considers case insensitivity for a first letter after delimeter in TOML entry`() = test(
      givenEntryPath = "alias.core.ext.foo",
      versionCatalogText = """
        [libraries]
        alias-Core-Ext-Foo = "aaa"
      """.trimIndent(),
      expectedToFind = "alias-Core-Ext-Foo = \"aaa\""
    )

    @Test
    fun `test findEntryElement considers case sensitivity for non-first letter after delimiter in TOML entry`() = test(
      givenEntryPath = "alias.core.ext.foo",
      versionCatalogText = """
        [libraries]
        alias-Core-eXt-Foo = "aaa"
      """.trimIndent()
    ) { foundElement ->
      assertNull(foundElement) { "In TOML version catalog, only first letter after delimiter is case insensitive." }
    }
  }

  @Nested
  @DisplayName("Tests for getEntriesFromSections(...) method")
  inner class GetEntriesFromSectionsTests {
    private fun test(
      sectionsFilter: Set<VersionCatalogSection>,
      expectedEntries: List<TomlCatalogEntry>,
    ) = runReadAction {
      val versionCatalogText = """
        [plugins]
        my-plugin-simple = { id = "my.plugin.id1", version.ref = "my-plugin-version" }
        my-plugin_various-delimiters = "my.plugin.id2:2.0"
        my-plugin-Uppercase-Letter = "my.plugin.id3:3.0"
        
        [versions]
        my-version-simple = "1.0.0"
        my-version_various-delimiters = "1.0.0"
        my-version_Uppercase-Letter = "1.0.0"
        
        [libraries]
        my-lib-simple = { module = "com.example:lib1", version.ref = "my-lib" }
        my-lib_various-delimiters = "com.example:lib2:1.0"
        my-lib-Uppercase-Letter = "com.example:lib3:1.0"
        
        [bundles]
        my-bundle-simple = ["my-bundle-lib1", "my-bundle-lib2"]
        my-bundle_various-delimiters = ["my-bundle-lib1"]
        my-bundle-Uppercase-Letter = ["my-bundle-lib2"]
        """.trimIndent()
      val tomlPsiFile = PsiFileFactory.getInstance(project)
        .createFileFromText(TomlLanguage, versionCatalogText) as TomlFile
      val entries = entrySearcher.getEntriesFromSections(tomlPsiFile, sectionsFilter)
      assertEqualsUnordered(expectedEntries, entries) {
        "The list of version catalog entries for search '$sectionsFilter' should match the expected"
      }
    }

    @Test
    fun `test getEntriesFromSections for libraries and bundles`() = test(
      sectionsFilter = setOf(LIBRARIES, BUNDLES),
      expectedEntries = listOf(
        TomlCatalogEntry("my.lib.simple", LIBRARIES),
        TomlCatalogEntry("my.lib.various.delimiters", LIBRARIES),
        TomlCatalogEntry("my.lib.uppercase.letter", LIBRARIES),
        TomlCatalogEntry("bundles.my.bundle.simple", BUNDLES),
        TomlCatalogEntry("bundles.my.bundle.various.delimiters", BUNDLES),
        TomlCatalogEntry("bundles.my.bundle.uppercase.letter", BUNDLES),
      )
    )

    @Test
    fun `test getEntriesFromSections for versions`() = test(
      sectionsFilter = setOf(VERSIONS),
      expectedEntries = listOf(
        TomlCatalogEntry("versions.my.version.simple", VERSIONS),
        TomlCatalogEntry("versions.my.version.various.delimiters", VERSIONS),
        TomlCatalogEntry("versions.my.version.uppercase.letter", VERSIONS),
      )
    )

    @Test
    fun `test getEntriesFromSections for plugins`() = test(
      sectionsFilter = setOf(PLUGINS),
      expectedEntries = listOf(
        TomlCatalogEntry("plugins.my.plugin.simple", PLUGINS),
        TomlCatalogEntry("plugins.my.plugin.various.delimiters", PLUGINS),
        TomlCatalogEntry("plugins.my.plugin.uppercase.letter", PLUGINS),
      )
    )
  }
}