// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.inspections.tests

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.inspections.runInspection
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.KotlinAmbiguousActualsInspection
import org.jetbrains.kotlin.idea.multiplatform.setupMppProjectFromDirStructure
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractK2AmbiguousActualsTest : AbstractMultiModuleTest() {
    protected fun dataFile(fileName: String): File = File(testDataPath, fileName)

    protected fun dataFile(): File = dataFile(fileName())

    protected open fun fileName(): String = KotlinTestUtils.getTestDataFileName(this::class.java, this.name) ?: (getTestName(false) + ".kt")

    override fun getTestDataDirectory(): File {
        return File(TestMetadataUtil.getTestDataPath(this::class.java))
    }


    fun doTest(unused: String) {
        setupMppProjectFromDirStructure(dataFile())
        val actionFile = project.allKotlinFiles().single {
            it.name == "top.kt" || it.name == "common-1.kt"
        }
        val virtualFilePath = actionFile.virtualFile!!.toNioPath()

        val ignoreDirective = IgnoreTests.DIRECTIVES.of(pluginMode)

        IgnoreTests.runTestIfNotDisabledByFileDirective(virtualFilePath, ignoreDirective) {
            val directiveFileText = actionFile.text
            withCustomCompilerOptions(directiveFileText, project, module) {
                val fileText = actionFile.text
                val ambiguousActualDirective = InTextDirectivesUtils.findLineWithPrefixRemoved(fileText, "// AMBIGUOUS_ACTUALS:")
                assertNotNull("Missing AMBIGUOUS_ACTUALS declaration in config file", ambiguousActualDirective)
                val expectedAmbiguousActualModules = ambiguousActualDirective!!.substringAfter(":").trim()
                    .takeIf { !it.isEmpty() }?.split(",")?.map { it.trim() } ?: emptyList()
                val problemDescriptors = runInspection(
                    KotlinAmbiguousActualsInspection::class.java,
                    project,
                    settings = null
                ).problemElements.values.map { it.descriptionTemplate }


                val ambiguousActuals = if (problemDescriptors.isEmpty()) {
                    emptyList()
                } else {
                    val differentErrors =
                        problemDescriptors.map { it.substringAfter("in modules", "").trim().split(",").map { it.trim() }.filter { !it.isBlank() } }
                            .distinct()
                    assertTrue("Found more than one problemDescriptor", differentErrors.size <= 1)
                    differentErrors.firstOrNull() ?: emptyList()
                }
                assertSameElements(ambiguousActuals, expectedAmbiguousActualModules)
            }
        }
    }

    protected open fun findAfterFile(editedFile: KtFile): PsiFile? = editedFile.containingDirectory?.findFile(editedFile.name + ".after")

}
