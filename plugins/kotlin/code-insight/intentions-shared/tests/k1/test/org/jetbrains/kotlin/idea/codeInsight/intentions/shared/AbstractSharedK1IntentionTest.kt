package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.intentions.AbstractK1IntentionTest
import java.io.File

abstract class AbstractSharedK1IntentionTest : AbstractK1IntentionTest() {
    override fun doTestFor(
        mainFile: File,
        pathToFiles: Map<String, PsiFile>,
        intentionAction: IntentionAction,
        fileText: String
    ) {
        doTestForInternal(mainFile, pathToFiles, intentionAction, fileText)
    }
}