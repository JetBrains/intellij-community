// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.catalog

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.readText
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.testFramework.gradle.KotlinGradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.params.ParameterizedTest

class GradleKtsVersionCatalogRenameTest: KotlinGradleCodeInsightTestCase() {

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testHasUsages(gradleVersion: GradleVersion) {
        testEmptyProject(gradleVersion) {
            val tomlName = "gradle/libs.versions.toml"
            val buildFileName = "build.gradle.kts"
            writeTextAndCommit(tomlName, """
            [libraries]
            foo-b<caret>ar = "org.example:foo:2.7.3"
                
            """.trimIndent())
            writeTextAndCommit(buildFileName,"""
            dependencies {
                implementation(libs.foo.bar)
            }
            """.trimIndent())

            runInEdtAndWait {
                codeInsightFixture.configureFromExistingVirtualFile(getFile(tomlName))
                val elementAtCaret = codeInsightFixture.elementAtCaret
                assertNotNull(elementAtCaret)
                codeInsightFixture.renameElementAtCaret("foo-baz")
                FileDocumentManager.getInstance().saveAllDocuments()
                getFile(tomlName).readText().contains("foo-baz = \"org.example:foo:2.7.3\"")
                getFile(buildFileName).readText().contains("implementation(libs.foo.baz)")

            }
        }
    }
}