// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.completion.test.ExpectedCompletionUtils
import org.jetbrains.kotlin.idea.completion.test.ExpectedCompletionUtils.BLOCK_CODE_FRAGMENT
import org.jetbrains.kotlin.idea.core.util.CodeFragmentUtils
import org.jetbrains.kotlin.idea.debugger.core.CodeFragmentContextTuner
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.test.utils.withExtension
import java.io.File
import kotlin.test.assertTrue

internal fun KtCodeFragment.checkImports(testFile: File) {
    val importList = importsAsImportList()
    val importsText = StringUtil.convertLineSeparators(importList?.text ?: "")

    val importsAfterFile = testFile.withExtension(".kt.after.imports")
    if (importsAfterFile.exists()) {
        KotlinTestUtils.assertEqualsToFile(importsAfterFile, importsText)
    } else {
        assertTrue(importsText.isEmpty(), "Unexpected imports found: $importsText")
    }
}

internal fun JavaCodeInsightTestFixture.configureByCodeFragment(filePath: String, useFirCodeFragment: Boolean = false) {
    configureByFile(File(filePath).name)

    val elementAt = file?.findElementAt(caretOffset)

    val isBlock = InTextDirectivesUtils.isDirectiveDefined(file.text, BLOCK_CODE_FRAGMENT)
    val file = createCodeFragment(filePath, elementAt!!, isBlock, useFirCodeFragment)

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

private fun createCodeFragment(filePath: String, contextElement: PsiElement, isBlock: Boolean, useFirCodeFragment: Boolean): KtCodeFragment {
    val contextTuner = CodeFragmentContextTuner.getInstance()
    val effectiveContextElement = contextTuner.tuneContextElement(contextElement)

    val fileNameForFragment = if (useFirCodeFragment) {
        "$filePath.fragment.k2"
    }
    else {
        "$filePath.fragment"
    }
    val fileForFragment = File(fileNameForFragment)
    val codeFragmentText = FileUtil.loadFile(fileForFragment, true).trim()
    val psiFactory = KtPsiFactory(contextElement.project)

    return if (isBlock) {
        psiFactory.createBlockCodeFragment(codeFragmentText, effectiveContextElement)
    } else {
        psiFactory.createExpressionCodeFragment(codeFragmentText, effectiveContextElement)
    }
}
