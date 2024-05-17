// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.inspections.tests

import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.inspections.runInspection
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.NoActualForExpectInspection
import java.io.File

abstract class AbstractK2ActualExpectTest : AbstractK2MultiplatformTest() {
    override fun setUp() {
        super.setUp()
    }

    override fun doTest(testPath: String) {
        super.doTest(testPath)
        val configFile = File(testPath, "missing_actuals.txt")
        assertExists(configFile)
        val fileText = configFile.readText()
        val missingActualDirective = InTextDirectivesUtils.findLineWithPrefixRemoved(fileText, "// MISSING_ACTUALS:")
        assertNotNull("Missing actuals declaration", missingActualDirective)
        val expectedMissingActualModules = missingActualDirective!!.substringAfter(":").trim()
            .takeIf { !it.isEmpty() }?.split(",")?.map { it.trim() } ?: emptyList()
        val problemDescriptors = runInspection(
            NoActualForExpectInspection::class.java,
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
        assertSameElements(expectedMissingActualModules, missingActuals)
    }

    override fun tearDown() {
        super.tearDown()
    }
}