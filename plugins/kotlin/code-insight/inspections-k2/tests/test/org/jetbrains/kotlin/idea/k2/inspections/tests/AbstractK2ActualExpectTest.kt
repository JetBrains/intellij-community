// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.inspections.tests

import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.inspections.runInspection
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.KotlinNoActualForExpectInspection
import org.jetbrains.kotlin.idea.test.KotlinLightMultiplatformCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.configureMultiPlatformModuleStructure
import java.io.File
abstract class AbstractK2ActualExpectTest : KotlinLightMultiplatformCodeInsightFixtureTestCase() {

    fun doTest(testPath: String) {
        myFixture.configureMultiPlatformModuleStructure(testPath)

        val configFile = File(testPath)
        assertExists(configFile)
        val fileText = configFile.readText()
        val missingActualDirective = InTextDirectivesUtils.findLineWithPrefixRemoved(fileText, "// MISSING_ACTUALS:")
        assertNotNull("Missing MISSING_ACTUALS declaration in config file", missingActualDirective)
        val expectedMissingActualModules = missingActualDirective!!.substringAfter(":").trim()
            .takeIf { !it.isEmpty() }?.split(",")?.map { it.trim() } ?: emptyList()
        val problemDescriptors = runInspection(
            KotlinNoActualForExpectInspection::class.java,
            project,
            settings = null
        ).problemElements.values.map { it.descriptionTemplate }

        assertTrue("Found more than one problemDescriptor", problemDescriptors.size <= 1)
        val problemDescriptor = problemDescriptors.firstOrNull()
        val missingActuals = if (problemDescriptor.isNullOrEmpty()) {
            emptyList()
        } else {
            problemDescriptor.substringAfter(":").trim().split(",").map { it.trim() }.filter { !it.isBlank() }
        }
        assertSameElements(missingActuals, expectedMissingActualModules)
    }

    override fun tearDown() {
        super.tearDown()
    }
}