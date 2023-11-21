// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.catalog

import com.intellij.testFramework.runInEdtAndWait
import com.intellij.usageView.UsageInfo
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.testFramework.gradle.KotlinGradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest

class GradleKtsVersionCatalogFindUsagesTest: KotlinGradleCodeInsightTestCase() {

    private fun testVersionCatalogFindUsages(
        version: GradleVersion, versionCatalogText: String, buildGradleText: String,
        checker: (Collection<UsageInfo>) -> Unit
    ) {
        checkCaret(versionCatalogText)
        testEmptyProject(version) {
            writeTextAndCommit("gradle/libs.versions.toml", versionCatalogText)
            writeTextAndCommit("build.gradle.kts", buildGradleText)
            runInEdtAndWait {
                codeInsightFixture.configureFromExistingVirtualFile(getFile("gradle/libs.versions.toml"))
                val elementAtCaret = codeInsightFixture.elementAtCaret
                assertNotNull(elementAtCaret)
                val usages = codeInsightFixture.findUsages(elementAtCaret)
                checker(usages)
            }
        }

    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testHasUsages(gradleVersion: GradleVersion) {
        testVersionCatalogFindUsages(
            gradleVersion, """
      [libraries]
      fo<caret>o-bar = "org.example:foo:2.7.3"
    """.trimIndent(), """
       dependencies {
           implementation(libs.foo.bar)
       }
    """.trimIndent()
        ) {
            assert(it.isNotEmpty())
            assert(it.toList()[0].element?.text == "bar")
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testHasNoUsages(gradleVersion: GradleVersion) {
        testVersionCatalogFindUsages(
            gradleVersion,
            """
      [versions]
      foo = "4.7.6"
      [libraries]
      aaa-bbb = { group = "org.example", name = "foo", version.ref = "foo" }
      [bundles]
      bund<caret>le = ["aaa-bbb"]
    """.trimIndent(), """
       dependencies {
           implementation(libs.aaa.bbb)
       }
       """.trimIndent()
        ) {
            assert(it.isEmpty())
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testFindUsagesOfBundles(gradleVersion: GradleVersion) {
        testVersionCatalogFindUsages(
            gradleVersion, """
      [versions]
      foo = "4.7.6"
      [libraries]
      aaa-bbb = { group = "org.example", name = "foo", version.ref = "foo" }
      [bundles]
      bund<caret>le = ["aaa-bbb"]
    """.trimIndent(), """
        dependencies{
            implementation(libs.bundles.bundle)
        }
    """.trimIndent()
        ) {
            assertNotNull(it.toList()[0].element?.text == "bundle")
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testNestedProject(gradleVersion: GradleVersion) {
        testEmptyProject(gradleVersion) {
            writeTextAndCommit(
                "gradle/libs.versions.toml",
                """
      [libraries]
      fo<caret>o-bar = "org.example:foo:2.7.3"
    """.trimIndent())
            writeTextAndCommit(
                "settings.gradle.kts",
                """
                    rootProject.name = "empty-project"
                    include("app")
                    """.trimIndent()
            )
            writeTextAndCommit("build.gradle.kts", "")
            writeTextAndCommit("app/build.gradle.kts", """
                dependencies{
                  implementation(libs.foo.bar)
                }
            """.trimIndent())
            runInEdtAndWait {
                codeInsightFixture.configureFromExistingVirtualFile(getFile("gradle/libs.versions.toml"))
                val elementAtCaret = codeInsightFixture.elementAtCaret
                assertNotNull(elementAtCaret)
                val usages = codeInsightFixture.findUsages(elementAtCaret)
                assertTrue(usages.isNotEmpty())
                assertTrue(usages.toList()[0].element?.parent?.parent?.text == "libs.foo.bar")
            }
        }
    }
}