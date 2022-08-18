// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.addImport

import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.AbstractImportsTest
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.util.ImportDescriptorResult
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils

abstract class AbstractAddImportTest : AbstractImportsTest() {
    override fun doTest(file: KtFile): String? {
        var descriptorName = InTextDirectivesUtils.stringWithDirective(file.text, "IMPORT")

        var filter: (DeclarationDescriptor) -> Boolean = { true }
        if (descriptorName.startsWith("class:")) {
            filter = { it is ClassDescriptor }
            descriptorName = descriptorName.substring("class:".length).trim()
        }

        val descriptors = file.resolveImportReference(FqName(descriptorName)).filter(filter)

        when {
            descriptors.isEmpty() -> error("No descriptor $descriptorName found")

            descriptors.size > 1 -> error("Multiple descriptors found:\n    " + descriptors.joinToString("\n    ") {
                DescriptorRenderer.FQ_NAMES_IN_TYPES.render(it)
            })

            else -> {
                val success = ImportInsertHelper.getInstance(project)
                    .importDescriptor(file, descriptors.single()) != ImportDescriptorResult.FAIL
                if (!success) {
                    val document = PsiDocumentManager.getInstance(project).getDocument(file)!!
                    document.replaceString(0, document.textLength, "Failed to add import")
                    PsiDocumentManager.getInstance(project).commitAllDocuments()
                }
            }
        }

        return null
    }
}