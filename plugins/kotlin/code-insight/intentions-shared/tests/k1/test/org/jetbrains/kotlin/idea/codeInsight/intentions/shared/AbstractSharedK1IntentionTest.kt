package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.intentions.AbstractK1IntentionTest
import java.io.File

abstract class AbstractSharedK1IntentionTest : AbstractK1IntentionTest() {
    override fun doTest(unused: String) {
        val mainFile = File(unused)
        val fileText = FileUtil.loadFile(mainFile, true)
        if (InTextDirectivesUtils.isDirectiveDefined(fileText, IgnoreTests.DIRECTIVES.IGNORE_K1)) return

        super.doTest(unused)
    }

    override fun doTestFor(
        mainFile: File,
        pathToFiles: Map<String, PsiFile>,
        intentionAction: IntentionAction,
        fileText: String
    ) {
        doTestForInternal(mainFile, pathToFiles, intentionAction, fileText)
    }
}