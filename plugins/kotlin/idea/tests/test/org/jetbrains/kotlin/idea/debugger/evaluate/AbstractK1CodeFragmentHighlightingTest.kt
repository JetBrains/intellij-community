// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.codeInspection.InspectionProfileEntry
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

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

    override fun configureByCodeFragment(filePath: String) {
        myFixture.configureByK1ModeCodeFragment(filePath)
    }

    private val inspectionDirectives: List<String> =
        buildList {
            if (isFirPlugin) this += "K2INSPECTION_CLASS"
            this += "INSPECTION_CLASS"
        }
}