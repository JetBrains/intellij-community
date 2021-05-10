// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.completion.test.ExpectedCompletionUtils
import org.jetbrains.kotlin.idea.completion.test.ExpectedCompletionUtils.BLOCK_CODE_FRAGMENT
import org.jetbrains.kotlin.idea.debugger.getContextElement
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File
import kotlin.test.assertTrue

internal fun KtCodeFragment.checkImports(file: File) {
    val importList = importsAsImportList()
    val importsText = StringUtil.convertLineSeparators(importList?.text ?: "")
    val fragmentAfterFile = File(file.parent, file.name + ".after.imports")

    if (fragmentAfterFile.exists()) {
        KotlinTestUtils.assertEqualsToFile(fragmentAfterFile, importsText)
    } else {
        assertTrue(importsText.isEmpty(), "Unexpected imports found: $importsText")
    }
}

internal fun JavaCodeInsightTestFixture.configureByCodeFragment(filePath: String) {
    configureByFile(File(filePath).name)

    val elementAt = file?.findElementAt(caretOffset)

    val isBlock = InTextDirectivesUtils.isDirectiveDefined(file.text, BLOCK_CODE_FRAGMENT)
    val file = createCodeFragment(filePath, elementAt!!, isBlock)

    val typeStr = InTextDirectivesUtils.findStringWithPrefixes(getFile().text, "// ${ExpectedCompletionUtils.RUNTIME_TYPE} ")
    file.putCopyableUserData(KtCodeFragment.RUNTIME_TYPE_EVALUATOR) {
        if (typeStr != null) {
            val codeFragment = KtPsiFactory(project).createBlockCodeFragment(
                "val xxx: $typeStr",
                PsiTreeUtil.getParentOfType(elementAt, KtElement::class.java)
            )
            val context = codeFragment.analyzeWithContent()
            val typeReference: KtTypeReference =
                PsiTreeUtil.getChildOfType(codeFragment.getContentElement().firstChild, KtTypeReference::class.java)!!
            context[BindingContext.TYPE, typeReference]
        } else {
            null
        }
    }

    configureFromExistingVirtualFile(file.virtualFile!!)
}

internal fun createCodeFragment(filePath: String, contextElement: PsiElement, isBlock: Boolean): KtCodeFragment {
    val fileForFragment = File("$filePath.fragment")
    val codeFragmentText = FileUtil.loadFile(fileForFragment, true).trim()
    val psiFactory = KtPsiFactory(contextElement.project)

    return if (isBlock) {
        psiFactory.createBlockCodeFragment(codeFragmentText, getContextElement(contextElement))
    } else {
        psiFactory.createExpressionCodeFragment(codeFragmentText, getContextElement(contextElement))
    }
}
