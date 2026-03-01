// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.completion.test.ExpectedCompletionUtils
import org.jetbrains.kotlin.idea.core.util.CodeFragmentUtils
import org.jetbrains.kotlin.idea.debugger.core.CodeFragmentContextTuner
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import java.io.File

@OptIn(KaExperimentalApi::class)
internal fun JavaCodeInsightTestFixture.configureByK1ModeCodeFragment(filePath: String) {
    configureByFile(File(filePath).name)

    val elementAt = file?.findElementAt(caretOffset)

    val isBlock = InTextDirectivesUtils.isDirectiveDefined(file.text, ExpectedCompletionUtils.BLOCK_CODE_FRAGMENT)
    val file = createK1ModeCodeFragment(filePath, elementAt!!, isBlock)

    val typeStr = InTextDirectivesUtils.findStringWithPrefixes(getFile().text, "// ${ExpectedCompletionUtils.RUNTIME_TYPE} ")

    file.putCopyableUserData(CodeFragmentUtils.RUNTIME_TYPE_EVALUATOR) {
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

private fun createK1ModeCodeFragment(filePath: String, contextElement: PsiElement, isBlock: Boolean): KtCodeFragment {
    val contextTuner = CodeFragmentContextTuner.getInstance()
    val effectiveContextElement = contextTuner.tuneContextElement(contextElement)

    val fileNameForFragment = "$filePath.fragment"

    val fileForFragment = File(fileNameForFragment)
    val codeFragmentText = FileUtil.loadFile(fileForFragment, true).trim()
    val psiFactory = org.jetbrains.kotlin.psi.KtPsiFactory(contextElement.project)

    return if (isBlock) {
        psiFactory.createBlockCodeFragment(codeFragmentText, effectiveContextElement)
    } else {
        psiFactory.createExpressionCodeFragment(codeFragmentText, effectiveContextElement)
    }
}