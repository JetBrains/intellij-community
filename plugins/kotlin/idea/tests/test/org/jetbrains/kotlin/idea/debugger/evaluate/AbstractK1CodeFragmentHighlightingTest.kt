// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.checkers.AbstractKotlinHighlightVisitorTest
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractCodeFragmentHighlightingTest : AbstractKotlinHighlightVisitorTest() {
    override fun doTest(filePath: String) {
        myFixture.configureByCodeFragment(filePath, useFirCodeFragment = isFirPlugin)
        checkHighlighting(filePath)
    }

    protected open fun doTestWithImport(filePath: String) {
        myFixture.configureByCodeFragment(filePath, useFirCodeFragment = isFirPlugin)

        project.executeWriteCommand("Imports insertion") {
            val fileText = FileUtil.loadFile(File(filePath), true)
            val file = myFixture.file as KtFile
            InTextDirectivesUtils.findListWithPrefixes(fileText, "// IMPORT: ").forEach {
                doImport(file, it)
            }
        }

        checkHighlighting(filePath)
    }

    protected open fun doImport(file: KtFile, importName: String) {}

    protected open fun checkHighlighting(filePath: String) {
        myFixture.checkHighlighting(true, false, false)
    }
}

abstract class AbstractK1CodeFragmentHighlightingTest : AbstractCodeFragmentHighlightingTest() {
    override fun doImport(file: KtFile, importName: String) {
        val descriptor = file.resolveImportReference(FqName(importName)).singleOrNull()
            ?: error("Could not resolve descriptor to import: $importName")
        ImportInsertHelper.getInstance(project).importDescriptor(file, descriptor)
    }

    override fun checkHighlighting(filePath: String) {
        val inspectionName =
            inspectionDirectives.firstNotNullOfOrNull {
                InTextDirectivesUtils.findStringWithPrefixes(
                    File(filePath).readText(),
                    "// $it: "
                )
            }
        if (inspectionName != null) {
            val inspection = Class.forName(inspectionName).getDeclaredConstructor().newInstance() as InspectionProfileEntry
            myFixture.enableInspections(inspection)
            try {
                myFixture.checkHighlighting(true, false, false)
            } finally {
                myFixture.disableInspections(inspection)
            }
            return
        }

        super.checkHighlighting(filePath)
    }

    private val inspectionDirectives: List<String> =
        buildList {
            if (isFirPlugin) this += "K2INSPECTION_CLASS"
            this += "INSPECTION_CLASS"
        }
}