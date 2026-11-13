// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.openapi.roots.PackageIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.codeInsight.inspections.PackageDirectoryMismatchInspection
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinSingleFileSourceRootProjectDescriptor
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.test.util.invalidateCaches

/**
 * Checks [PackageDirectoryMismatchInspection] for a file whose directory has no package.
 * This is the case under a source root whose `packageMatchesDirectory` is `false` (`// NO_DIRECTORY_PACKAGES`),
 * and for a single-file source root (`// SINGLE_FILE_SOURCE_ROOT`). The inspection must not report a mismatch there.
 */
@TestRoot("code-insight/inspections-k2/tests")
@TestMetadata("testData/inspections/packageDirectoryMismatchWithoutDirectoryPackages")
class PackageDirectoryMismatchInspectionWithoutDirectoryPackagesTest : KotlinLightCodeInsightFixtureTestCase() {
    fun testNonEmptyPackage() = doTest()

    fun testNonEmptyPackageWithImplicitPackagePrefix() = doTest(reportImplicitPackagePrefix = true)

    fun testEmptyPackage() = doTest()

    fun testSingleFileSourceRoot() = doTest()

    private fun doTest(reportImplicitPackagePrefix: Boolean = false) {
        val inspection = PackageDirectoryMismatchInspection()
        inspection.reportImplicitPackagePrefix = reportImplicitPackagePrefix
        myFixture.enableInspections(inspection)
        val file = addTestFile(dataFile().readText())
        assertNull(
            "The directory of $file has a package, so the project descriptor does not give the expected layout",
            PackageIndex.getInstance(project).getPackageNameByDirectory(file.parent),
        )
        myFixture.configureFromExistingVirtualFile(file)
        myFixture.checkHighlighting(true, false, true)
    }

    /** Puts [text] into the single-file source root of the descriptor, or into `a/b/Foo.kt` under the light source root. */
    private fun addTestFile(text: String): VirtualFile =
        if (projectDescriptor === KotlinSingleFileSourceRootProjectDescriptor) {
            KotlinSingleFileSourceRootProjectDescriptor.sourceRootFile(module).also { myFixture.saveText(it, text) }
        }
        else {
            myFixture.addFileToProject("a/b/Foo.kt", text).virtualFile
        }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }
}
