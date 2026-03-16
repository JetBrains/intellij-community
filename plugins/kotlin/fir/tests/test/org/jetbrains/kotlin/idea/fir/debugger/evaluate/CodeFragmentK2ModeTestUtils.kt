// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.debugger.evaluate

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.completion.test.ExpectedCompletionUtils
import org.jetbrains.kotlin.idea.debugger.core.CodeFragmentContextTuner
import org.jetbrains.kotlin.idea.debugger.evaluate.util.KotlinK2CodeFragmentUtils
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File

@OptIn(KaExperimentalApi::class)
fun JavaCodeInsightTestFixture.configureByK2ModeCodeFragment(filePath: String) {
    configureByFile(File(filePath).name)

    val elementAt = file?.findElementAt(caretOffset)

    val isBlock = InTextDirectivesUtils.isDirectiveDefined(file.text, ExpectedCompletionUtils.BLOCK_CODE_FRAGMENT)
    val file = createK2ModeCodeFragment(filePath, elementAt!!, isBlock)

    val typeStr = InTextDirectivesUtils.findStringWithPrefixes(getFile().text, "// ${ExpectedCompletionUtils.RUNTIME_TYPE} ")

    file.putCopyableUserData(KotlinK2CodeFragmentUtils.RUNTIME_TYPE_EVALUATOR_K2) { expression ->
        if (typeStr != null) {
            analyze(expression) {
                val kaType = buildClassType(ClassId.topLevel(FqName(typeStr)))
                kaType.createPointer()
            }
        } else {
            null
        }
    }
    configureFromExistingVirtualFile(file.virtualFile!!)
}

private fun createK2ModeCodeFragment(filePath: String, contextElement: PsiElement, isBlock: Boolean): KtCodeFragment {
    val contextTuner = CodeFragmentContextTuner.getInstance()
    val effectiveContextElement = contextTuner.tuneContextElement(contextElement)

    val fragmentExtensions = listOf(
        "fragment.k2",
        "fragment",
    )

    val fileForFragment = fragmentExtensions.firstNotNullOfOrNull { extension ->
        val candidateFile = File("$filePath.$extension")
        candidateFile.takeIf { it.exists() }
    } ?: error(".fragment file corresponding to '$filePath' should exist")

    val codeFragmentText = FileUtil.loadFile(fileForFragment, true).trim()
    val psiFactory = KtPsiFactory(contextElement.project)

    return if (isBlock) {
        psiFactory.createBlockCodeFragment(codeFragmentText, effectiveContextElement)
    } else {
        psiFactory.createExpressionCodeFragment(codeFragmentText, effectiveContextElement)
    }
}