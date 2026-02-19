// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.completion

import com.intellij.openapi.util.io.FileUtil.loadFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.completion.test.AbstractJvmBasicCompletionTestBase
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File

abstract class AbstractK2TypeCodeFragmentCompletionTest : AbstractJvmBasicCompletionTestBase() {
    override fun configureFixture(testPath: String) {
        myFixture.configureByFile(File(testPath).name)
        val elementAt = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.caretOffset), KtElement::class.java, false)!!
        val fileForFragment = File("$testPath.fragment")
        val codeFragmentText = loadFile(fileForFragment, true).trim()
        val file = KtPsiFactory(myFixture.project).createTypeCodeFragment(codeFragmentText, elementAt)
        myFixture.configureFromExistingVirtualFile(file.virtualFile!!)
    }
}

