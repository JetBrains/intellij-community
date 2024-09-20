// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers

import com.intellij.openapi.editor.Editor
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndGet
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinMppTestsContext
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.highlighting.TestFeatureWithFileMarkup
import org.jetbrains.kotlin.gradle.multiplatformTests.writeAccess
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

interface ReferenceTargetCheckerDsl {
    fun TestConfigurationDslScope.checkReference(check: (referencedDeclaration: KtDeclaration) -> Unit) {
        writeAccess.getConfiguration(ReferenceTargetChecker).checkReferencedDeclaration = check
    }
}

class ReferenceTargetCheckerConfig {
    var checkReferencedDeclaration: (referencedDeclaration: KtDeclaration) -> Unit = { }
}

object ReferenceTargetChecker : TestFeatureWithFileMarkup<ReferenceTargetCheckerConfig> {
    override fun createDefaultConfiguration(): ReferenceTargetCheckerConfig {
        return ReferenceTargetCheckerConfig()
    }

    override fun KotlinMppTestsContext.afterImport() {
        runInEdtAndGet {
            val allKotlinFiles = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, ProjectScope.getProjectScope(testProject))
            for (virtualFile in allKotlinFiles) {
                codeInsightTestFixture.openFileInEditor(virtualFile)
                if (codeInsightTestFixture.caretOffset == 0) continue

                val caretElement = codeInsightTestFixture.file.findElementAt(codeInsightTestFixture.caretOffset)
                    ?: error("No element at caret in the file '$virtualFile'")
                val referenceExpression = caretElement.parent as? KtNameReferenceExpression
                    ?: error("Caret is expected at reference expression")
                val element = referenceExpression.mainReference.resolve() as KtElement
                val navigationElement = element.navigationElement as? KtDeclaration
                    ?: error("Expected reference target to be declaration, but got ${element.javaClass} instead")

                testConfiguration.getConfiguration(ReferenceTargetChecker).checkReferencedDeclaration(navigationElement)
            }
        }
    }

    override fun KotlinMppTestsContext.restoreMarkup(text: String, editor: Editor): String? {
        val caretOffset = editor.caretModel.offset.takeIf { it > 0 } ?: return null
        if (caretOffset > text.length) return null

        return text.substring(0, caretOffset) + CodeInsightTestFixture.CARET_MARKER + text.substring(caretOffset)
    }
}
