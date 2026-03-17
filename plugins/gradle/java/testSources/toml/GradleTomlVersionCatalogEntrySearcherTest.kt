/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.toml

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
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
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

      testFindEntryElement("groovy.core", """
      libraries = { groovy-core = "lib" }
      """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("groovy-core = \"lib\"", it.text)
      }

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

      testFindEntryElement("bundles.bundle.core", """
      bundles = { bundle-core = ["lib"] }
      """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("bundle-core = [\"lib\"]", it.text)
      }

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

      testFindEntryElement("plugins.plugin.core", """
      plugins = { plugin_core = ["plugin"] }
      """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("plugin_core = [\"plugin\"]", it.text)
      }

      testFindEntryElement("plugins.plugin.core", """
      plugins.plugin_core = "plugin"
      """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("plugins.plugin_core = \"plugin\"", it.text)
      }
    }

    @Test
    fun `test findEntryElement for a complex entry path`() {
      testFindEntryElement("alias_core-ext", """
      [libraries]
      alias-core-ext = "aaa"
      """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("alias-core-ext = \"aaa\"", it.text)
      }

      testFindEntryElement("alias.core.ext", """
      [libraries]
      alias_core-ext = "aaa"
      """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("alias_core-ext = \"aaa\"", it.text)
      }

      testFindEntryElement("alias.core", """
      [libraries]
      alias-core-ext = "aaa"
      alias-core = "aaa"
      """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("alias-core = \"aaa\"", it.text)
      }
    }

    @Test
    fun `test findEntryElement when first letter after the separator is Uppercase`() {
      testFindEntryElement("alias_core-ext", """
        [libraries]
        alias-Core-ext = "aaa"
        """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("alias-Core-ext = \"aaa\"", it.text)
      }

      testFindEntryElement("alias.core.ext", """
      [libraries]
      alias-core-Ext = "aaa"
      """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("alias-core-Ext = \"aaa\"", it.text)
      }

      testFindEntryElement("alias.core", """
      [libraries]
      alias-core-ext = "aaa"
      alias-Core = "aaa"
      """.trimIndent()
      ) {
        assertNotNull(it)
        assertEquals("alias-Core = \"aaa\"", it.text)
      }

      testFindEntryElement("alias.core.Ext", """
      [libraries]
      alias-core-ext = "aaa"
      """.trimIndent()
      ) {
        assertNull(it)
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