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
  private val entrySearcher = GradleTomlVersionCatalogEntrySearcher()
  private val project by projectFixture()

  @Nested
  @DisplayName("Tests for findEntryElement(...) method")
  inner class FindEntryElementTests {

    private fun testFindEntryElement(
      tomlKeyPath: String,
      versionCatalogText: String,
      checker: (PsiElement?) -> Unit,
    ) = runReadAction {
      val tomPsiFile = PsiFileFactory.getInstance(project)
        .createFileFromText(TomlLanguage, versionCatalogText) as TomlFile
      val element = entrySearcher.findEntryElement(tomPsiFile, tomlKeyPath)
      checker(element)
    }

    @Test
    fun `test findEntryElement for a library entry`() {
      testFindEntryElement("groovy.core", """
        plugins.groovy-core = "plugin"
        [libraries]
        groovy-core = "lib"
        """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("groovy-core = \"lib\"", it.text)
      }
    }

    @Test
    fun `test findEntryElement for a library entry when libraries is inline table`() {
      testFindEntryElement("groovy.core", """
        libraries = { groovy-core = "lib" }
        """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("groovy-core = \"lib\"", it.text)
      }
    }

    @Test
    fun `test findEntryElement for a library entry when libraries table name is a part of alias`() {
      testFindEntryElement("groovy.core", """
        libraries.groovy-core = "lib"
        """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("libraries.groovy-core = \"lib\"", it.text)
      }
    }

    @Test
    fun `test findEntryElement for a bundle entry`() {
      testFindEntryElement("bundles.bundle", """
        [bundles]
        bundle = ["lib"]
        """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("bundle = [\"lib\"]", it.text)
      }
    }

    @Test
    fun `test findEntryElement for a bundle entry when bundles is inline table`() {
      testFindEntryElement("bundles.bundle.core", """
        bundles = { bundle-core = ["lib"] }
        """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("bundle-core = [\"lib\"]", it.text)
      }
    }

    @Test
    fun `test findEntryElement for a bundle entry when bundles table name is a part of alias`() {
      testFindEntryElement("bundles.bundle.core", """
        bundles.bundle-core = "lib"
        """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("bundles.bundle-core = \"lib\"", it.text)
      }
    }

    @Test
    fun `test findEntryElement for a version entry`() {
      testFindEntryElement("versions.my.foo", """
        [versions]
        my-foo = ["foo"]
        my-foo-bar = ["foo-bar"]
        """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("my-foo = [\"foo\"]", it.text)
      }
    }

    @Test
    fun `test findEntryElement for a plugin entry`() {
      testFindEntryElement("plugins.plugin", """
        [plugins]
        plugin = "plugin"
        """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("plugin = \"plugin\"", it.text)
      }
    }

    @Test
    fun `test findEntryElement for a plugin entry when plugins is inline table`() {
      testFindEntryElement("plugins.plugin.core", """
        plugins = { plugin_core = ["plugin"] }
        """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("plugin_core = [\"plugin\"]", it.text)
      }
    }

    @Test
    fun `test findEntryElement for a plugin entry when plugins table name is a part of alias`() {
      testFindEntryElement("plugins.plugin.core", """
        plugins.plugin_core = "plugin"
        """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("plugins.plugin_core = \"plugin\"", it.text)
      }
    }

    @Test
    fun `test findEntryElement when a TOML entry name has various delimiters`() {
      testFindEntryElement("alias.core.ext", """
        [libraries]
        alias_core-ext = "aaa"
        """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("alias_core-ext = \"aaa\"", it.text)
      }
    }

    @Test
    fun `test findEntryElement when given entry path has various delimiters`() {
      testFindEntryElement("alias_core-ext", """
        [libraries]
        alias-core-ext = "aaa"
        """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("alias-core-ext = \"aaa\"", it.text)
      }
    }

    @Test
    fun `test findEntryElement shouldn't return an entry with name containing given path but larger`() {
      testFindEntryElement("alias.core", """
        [libraries]
        alias-core-ext = "aaa"
        """.trimIndent()
      ) {
        assertNull(it) { "findEntryElement shouldn return an entry having more letters than given path." }
      }
    }

    @Test
    fun `test findEntryElement considers case insensitivity for a first letter after delimeter in TOML entry`() {
      testFindEntryElement("alias.core.ext.foo", """
        [libraries]
        alias-Core-Ext-Foo = "aaa"
        """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("alias-Core-Ext-Foo = \"aaa\"", it.text)
      }
    }

    @Test
    fun `test findEntryElement considers case sensitivity for non-first letter after delimiter in TOML entry`() {
      testFindEntryElement("alias.core.ext.foo", """
        [libraries]
        alias-Core-eXt-Foo = "aaa"
        """.trimIndent()
      ) {
        assertNull(it) { "In TOML version catalog, only first letter after delimiter is case insensitive." }
      }
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