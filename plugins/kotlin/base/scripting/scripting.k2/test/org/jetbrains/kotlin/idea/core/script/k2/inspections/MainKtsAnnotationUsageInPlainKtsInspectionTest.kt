// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k2.inspections

import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor

class MainKtsAnnotationUsageInPlainKtsInspectionTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): KotlinLightProjectDescriptor = KotlinLightProjectDescriptor.INSTANCE

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(MainKtsAnnotationUsageInPlainKtsInspection())
    }

    fun testDependsOnAnnotationOffersFileRename() = doTest("DependsOn")

    fun testImportAnnotationOffersFileRename() = doTest("Import")

    private fun doTest(annotationName: String) {
        myFixture.configureByText(
            KotlinFileType.INSTANCE,
            """
            @file:<caret>$annotationName("org.example:demo:1.0")
            println("ok")
            """.trimIndent()
        )

        myFixture.doHighlighting()

        val expectedName = "Rename file to ${myFixture.file.name.substringBeforeLast('.')}.main.kts"
        val fix = myFixture.findSingleIntention(expectedName)

        assertEquals(expectedName, fix.text)
        assertEquals(expectedName, fix.familyName)
    }
}
