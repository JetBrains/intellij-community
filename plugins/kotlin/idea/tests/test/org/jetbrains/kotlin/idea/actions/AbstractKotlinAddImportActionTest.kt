// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

abstract class AbstractKotlinAddImportActionTest : KotlinLightCodeInsightFixtureTestCase() {

    protected fun doTest(unused: String) {
        val fixture = myFixture

        val dependencySuffixes = listOf(".dependency.kt", ".dependency1.kt", ".dependency2.kt")
        for (suffix in dependencySuffixes) {
            val depFilePath = fileName().replace(".kt", suffix)
            if (File(testDataDirectory, depFilePath).exists()) {
                fixture.configureByFile(depFilePath)
            }
        }

        fixture.configureByFile(fileName())

        var actualVariants: List<List<DeclarationDescriptor>>? = null
        val executeListener = object : KotlinAddImportActionInfo.ExecuteListener {
            override fun onExecute(variants: List<List<DeclarationDescriptor>>) {
                assertNull(actualVariants)
                actualVariants = variants
            }
        }
        KotlinAddImportActionInfo.setExecuteListener(testRootDisposable, executeListener)

        val importFix = myFixture.availableIntentions.singleOrNull { it.familyName == "Import" } ?: error("No import fix available")
        importFix.invoke(project, editor, file)

        assertNotNull(actualVariants)

        val expectedVariantNames = InTextDirectivesUtils.findListWithPrefixes(fixture.file.text, "EXPECT_VARIANT_IN_ORDER")
        val expectedAbsentVariantNames = InTextDirectivesUtils.findListWithPrefixes(fixture.file.text, "EXPECT_VARIANT_NOT_PRESENT")

        if (expectedVariantNames.isNotEmpty() || expectedAbsentVariantNames.isNotEmpty()) {
            val actualVariantNames = actualVariants!!.flatten().map { it.variantName() }

            for (i in expectedVariantNames.indices) {
                assertTrue(
                    "mismatch at #$i: '${actualVariantNames[i]}' should start with '${expectedVariantNames[i]}'\nactual:\n${actualVariantNames.joinToString("\n")}",
                    actualVariantNames[i].contains(expectedVariantNames[i])
                )
            }

            for (expectedAbsentVariant in expectedAbsentVariantNames) {
                assertTrue("$expectedAbsentVariant should not be present",
                           actualVariantNames.none { it.contains(expectedAbsentVariant) })
            }
        }

        myFixture.checkResultByFile("${fileName()}.after")
    }

    private fun DeclarationDescriptor.variantName() = when(this) {
        is ClassDescriptor ->
            fqNameOrNull()?.toString()?.let { "class $it" } ?: toString()
        else ->
            toString()
    }
}
