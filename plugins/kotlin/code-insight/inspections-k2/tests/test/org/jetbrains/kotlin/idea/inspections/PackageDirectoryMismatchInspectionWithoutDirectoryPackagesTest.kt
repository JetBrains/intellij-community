// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.PackageIndex
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.runAll
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.kotlin.idea.codeInsight.inspections.PackageDirectoryMismatchInspection
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.test.util.invalidateCaches

/**
 * Checks [PackageDirectoryMismatchInspection] under a source root whose `packageMatchesDirectory` is `false`.
 * A directory under such a root has no package, so the inspection must not report a mismatch.
 */
class PackageDirectoryMismatchInspectionWithoutDirectoryPackagesTest : KotlinLightCodeInsightFixtureTestCase() {
    fun `test non-empty package`() = doTest("package com.example\n\nclass Foo\n")

    fun `test non-empty package with the implicit package prefix option`() =
        doTest("package com.example\n\nclass Foo\n", reportImplicitPackagePrefix = true)

    fun `test empty package`() = doTest("class Foo\n")

    private fun doTest(text: String, reportImplicitPackagePrefix: Boolean = false) {
        val inspection = PackageDirectoryMismatchInspection()
        inspection.reportImplicitPackagePrefix = reportImplicitPackagePrefix
        myFixture.enableInspections(inspection)
        val file = myFixture.addFileToProject("a/b/Foo.kt", text).virtualFile
        assertNull(
            "The directory of $file has a package, so the descriptor did not apply packageMatchesDirectory",
            PackageIndex.getInstance(project).getPackageNameByDirectory(file.parent),
        )
        myFixture.configureFromExistingVirtualFile(file)
        myFixture.checkHighlighting(true, false, true)
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = WithoutDirectoryPackagesDescriptor

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    /**
     * Sets `packageMatchesDirectory` to `false` on the Java source folders of the light module.
     */
    private object WithoutDirectoryPackagesDescriptor : KotlinWithJdkAndRuntimeLightProjectDescriptor() {
        override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
            super.configureModule(module, model, contentEntry)
            for (sourceFolder in contentEntry.sourceFolders) {
                sourceFolder.jpsElement.getProperties(JavaModuleSourceRootTypes.SOURCES)?.isPackageMatchesDirectory = false
            }
        }
    }
}
