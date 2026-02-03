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

import com.intellij.openapi.externalSystem.util.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.toml.lang.TomlLanguage
import org.toml.lang.psi.TomlFile

@TestApplication
class GradleFindTomlCatalogKeyTest {

  private val project by projectFixture()

  private fun testFindTomlCatalogKey(
    tomlKeyPath: String,
    versionCatalogText: String,
    checker: (PsiElement?) -> Unit,
  ) = runReadAction {
    val tomPsiFile = PsiFileFactory.getInstance(project)
      .createFileFromText(TomlLanguage, versionCatalogText) as TomlFile
    val element = findTomlCatalogKey(tomPsiFile, tomlKeyPath)
    checker(element)
  }

  @Test
  fun testFindInLibraries() {
    testFindTomlCatalogKey("groovy.core", """
      plugins.groovy-core = "plugin"
      [libraries]
      groovy-core = "lib"
      """.trimIndent()
    ) {
      assertNotNull(it)
      assertEquals("groovy-core = \"lib\"", it.text)
    }

    testFindTomlCatalogKey("groovy.core", """
      libraries = { groovy-core = "lib" }
      """.trimIndent()
    ) {
      assertNotNull(it)
      assertEquals("groovy-core = \"lib\"", it.text)
    }

    testFindTomlCatalogKey("groovy.core", """
      libraries.groovy-core = "lib"
      """.trimIndent()
    ) {
      assertNotNull(it)
      assertEquals("libraries.groovy-core = \"lib\"", it.text)
    }
  }

  @Test
  fun testFindInBundles() {
    testFindTomlCatalogKey("bundles.bundle", """
      [bundles]
      bundle = ["lib"]
      """.trimIndent()
    ) {
      assertNotNull(it)
      assertEquals("bundle = [\"lib\"]", it.text)
    }

    testFindTomlCatalogKey("bundles.bundle.core", """
      bundles = { bundle-core = ["lib"] }
      """.trimIndent()
    ) {
      assertNotNull(it)
      assertEquals("bundle-core = [\"lib\"]", it.text)
    }

    testFindTomlCatalogKey("bundles.bundle.core", """
      bundles.bundle-core = "lib"
      """.trimIndent()
    ) {
      assertNotNull(it)
      assertEquals("bundles.bundle-core = \"lib\"", it.text)
    }
  }

  @Test
  fun testFindInPlugins() {
    testFindTomlCatalogKey("plugins.plugin", """
      [plugins]
      plugin = "plugin"
      """.trimIndent()
    ) {
      assertNotNull(it)
      assertEquals("plugin = \"plugin\"", it.text)
    }

    testFindTomlCatalogKey("plugins.plugin.core", """
      plugins = { plugin_core = ["plugin"] }
      """.trimIndent()
    ) {
      assertNotNull(it)
      assertEquals("plugin_core = [\"plugin\"]", it.text)
    }

    testFindTomlCatalogKey("plugins.plugin.core", """
      plugins.plugin_core = "plugin"
      """.trimIndent()
    ) {
      assertNotNull(it)
      assertEquals("plugins.plugin_core = \"plugin\"", it.text)
    }
  }

  @Test
  fun testFindComplexPath() {
    testFindTomlCatalogKey("alias_core-ext", """
      [libraries]
      alias-core-ext = "aaa"
      """.trimIndent()
    ) {
      assertNotNull(it)
      assertEquals("alias-core-ext = \"aaa\"", it.text)
    }

    testFindTomlCatalogKey("alias.core.ext", """
      [libraries]
      alias-core-ext = "aaa"
      """.trimIndent()
    ) {
      assertNotNull(it)
      assertEquals("alias-core-ext = \"aaa\"", it.text)
    }

    testFindTomlCatalogKey("alias.core", """
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
  fun testFindFirstLetterWithDifferentCase() {
    testFindTomlCatalogKey("alias_core-ext", """
        [libraries]
        alias-Core-ext = "aaa"
        """.trimIndent()
    ) {
      assertNotNull(it)
      assertEquals("alias-Core-ext = \"aaa\"", it.text)
    }

    testFindTomlCatalogKey("alias.core.ext", """
      [libraries]
      alias-core-Ext = "aaa"
      """.trimIndent()
    ) {
      assertNotNull(it)
      assertEquals("alias-core-Ext = \"aaa\"", it.text)
    }

    testFindTomlCatalogKey("alias.core", """
      [libraries]
      alias-core-ext = "aaa"
      alias-Core = "aaa"
      """.trimIndent()
    ) {
      assertNotNull(it)
      assertEquals("alias-Core = \"aaa\"", it.text)
    }

    testFindTomlCatalogKey("alias.core.Ext", """
      [libraries]
      alias-core-ext = "aaa"
      """.trimIndent()
    ) {
      assertNull(it)
    }
  }
}