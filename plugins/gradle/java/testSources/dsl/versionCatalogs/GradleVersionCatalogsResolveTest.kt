// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.versionCatalogs

import com.intellij.idea.IJIgnore
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

class GradleVersionCatalogsResolveTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNavigationToTomlFile(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      testGotoDefinition("lib<caret>s") {
        assertInstanceOf(TomlFile::class.java, it)
        assertTrue((it as TomlFile).name == "libs.versions.toml")
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNavigationToTomlFile2(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      testGotoDefinition("lib<caret>s2") {
        assertInstanceOf(GroovyPsiElement::class.java, it)
        assertTrue(it.parentOfType<GrMethodCall>(true)!!.resolveMethod()!!.name == "libs2")
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNavigationToCustomCatalog(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      testGotoDefinition("lib<caret>s3") {
        assertInstanceOf(GroovyPsiElement::class.java, it)
        assertTrue(it.parentOfType<GrMethodCall>(true)!!.resolveMethod()!!.name == "libs3")
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNavigationToTomlEntry(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      testGotoDefinition("libs.groovy.cor<caret>e") {
        assertInstanceOf(TomlKeyValue::class.java, it)
        assertTrue((it as TomlKeyValue).key.text == "groovy-core")
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNavigationToTomlEntry2(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      testGotoDefinition("libs.groo<caret>vy.core") {
        assertInstanceOf(TomlKeyValue::class.java, it)
        assertTrue((it as TomlKeyValue).key.text == "groovy-core")
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNavigationToTomlEntry3(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      testGotoDefinition("libs.groovy.n<caret>io") {
        assertInstanceOf(TomlKeyValue::class.java, it)
        assertTrue((it as TomlKeyValue).key.text == "groovy-nio")
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNavigationToTomlEntry4(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      testGotoDefinition("libs.groovy.nio.anot<caret>her") {
        assertInstanceOf(TomlKeyValue::class.java, it)
        assertTrue((it as TomlKeyValue).key.text == "groovy-nio-another")
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNavigationToTomlTable(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      testGotoDefinition("libs.bun<caret>dles") {
        assertInstanceOf(TomlTable::class.java, it)
        assertTrue((it as TomlTable).header.text == "[bundles]")
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNavigationToLibraryInSettings(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      testGotoDefinition("libs3.foo.bar.ba<caret>z") {
        assertInstanceOf(GroovyPsiElement::class.java, it)
        assertTrue(it.parentOfType<GrMethodCall>(true)!!.argumentList.expressionArguments[0].text == "\"foo.bar.baz\"")
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNavigationToLibraryInSettings2(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      testGotoDefinition("libs3.foo.nn.m<caret>m") {
        assertInstanceOf(GroovyPsiElement::class.java, it)
        assertTrue(it.parentOfType<GrMethodCall>(true)!!.argumentList.expressionArguments[0].text == "\"foo-nn-mm\"")
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  @IJIgnore(issue = "IDEA-355847")
  fun testNavigationWithCapitalLetters(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      testGotoDefinition("libs2.getCheck().getCapital().getLe<caret>tter()") {
        assertInstanceOf(TomlKeyValue::class.java, it)
        assertTrue((it as TomlKeyValue).key.text == "check-Capital-Letter")
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNavigationFromIncludedBuildToItsDefaultToml(gradleVersion: GradleVersion) =
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      testGotoDefinition("includedBuild/build.gradle", "libs.for.inc<caret>luded.build") { psiElement ->
        verifyNavigationToToml(psiElement,
                               expectedTomlKey = "for_included-build",
                               endOfTomlPath = "includedBuild/gradle/libs.versions.toml")
      }
    }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNavigationFromIncludedBuildWithoutSettingsToItsDefaultToml(gradleVersion: GradleVersion) =
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      testGotoDefinition("includedBuildWithoutSettings/build.gradle", "libs.for.build.with<caret>out.settings") { psiElement ->
        verifyNavigationToToml(psiElement,
                               expectedTomlKey = "for_build-without-settings",
                               endOfTomlPath = "includedBuildWithoutSettings/gradle/libs.versions.toml")
      }
    }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNavigationFromSubprojectToItsParentsDefaultToml(gradleVersion: GradleVersion) =
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      testGotoDefinition("subproject/build.gradle", "libs.groo<caret>vy.core") { psiElement ->
        verifyNavigationToToml(psiElement,
                               expectedTomlKey = "groovy-core",
                               endOfTomlPath = "GradleVersionCatalogs-completion/gradle/libs.versions.toml")
      }
    }

  companion object {
    private fun verifyNavigationToToml(element: PsiElement, expectedTomlKey: String, endOfTomlPath: String) {
      val tomlKeyValue = assertInstanceOf(TomlKeyValue::class.java, element)
      assertEquals(expectedTomlKey, tomlKeyValue.key.text)
      val tomlPath = tomlKeyValue.containingFile.virtualFile.path
      assertTrue(tomlPath.endsWith(endOfTomlPath))
    }

    private val BASE_VERSION_CATALOG_FIXTURE = GradleTestFixtureBuilder.create("GradleVersionCatalogs-completion") {
      withSettingsFile {
        setProjectName("GradleVersionCatalogs-completion")
        addCode("""
          includeBuild("includedBuild")
          includeBuild("includedBuildWithoutSettings")
          include(":subproject")
          dependencyResolutionManagement {
              versionCatalogs {
                  libs2 {
                      from(files("gradle/my.toml"))
                  }
                  libs3 {
                      library("foo.bar.baz", "org.apache.groovy:groovy:4.0.0")
                      library("foo-nn-mm", "org.apache.groovy:groovy:4.0.0")
                  }
              }
          }
        """.trimIndent())
      }
      withFile("gradle/libs.versions.toml", /* language=TOML */ """
        [versions]
        groovy = "3.0.5"
        checkstyle = "8.37"

        [libraries]
        groovy-core = { module = "org.codehaus.groovy:groovy", version.ref = "groovy" }
        groovy-json = { module = "org.codehaus.groovy:groovy-json", version.ref = "groovy" }
        groovy-nio = { module = "org.codehaus.groovy:groovy-nio", version.ref = "groovy" }
        groovy-nio-another = { module = "org.codehaus.groovy:groovy-nio", version.ref = "groovy" }
        commons-lang3 = { group = "org.apache.commons", name = "commons-lang3", version = { strictly = "[3.8, 4.0[", prefer="3.9" } }

        [bundles]
        groovy = ["groovy-core", "groovy-json", "groovy-nio"]

        [plugins]
        jmh = { id = "me.champeau.jmh", version = "0.6.5" }
      """.trimIndent())
      withFile("gradle/my.toml", /* language=TOML */ """
        [libraries]
        aa-bb-cc = { module = "org.apache.groovy:groovy", version = "4.0.0" }
        check-Capital-Letter = { module = "org.apache.groovy:groovy", version = "4.0.0" }
      """.trimIndent())

      // included build without settings
      withFile("includedBuildWithoutSettings/gradle/libs.versions.toml", /* language=TOML */ """
        [libraries]
        for_build-without-settings = { module = "org.junit.jupiter:junit-jupiter" }
        """.trimIndent()
      )
      // included build with settings
      withSettingsFile("includedBuild") {
        setProjectName("includedBuild")
      }
      withFile("includedBuild/gradle/libs.versions.toml", /* language=TOML */ """
        [libraries]
        for_included-build = { module = "org.junit.jupiter:junit-jupiter" }
        """.trimIndent()
      )
    }
  }

}