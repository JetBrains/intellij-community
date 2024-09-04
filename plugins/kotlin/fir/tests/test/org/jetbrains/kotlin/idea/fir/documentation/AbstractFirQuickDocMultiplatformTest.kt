// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.documentation

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.documentation.impl.computeDocumentationBlocking
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.editor.quickDoc.AbstractQuickDocProviderTest.wrapToFileComparisonFailure
import org.jetbrains.kotlin.idea.test.KotlinLightMultiplatformCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.configureMultiPlatformModuleStructure
import java.io.File

abstract class AbstractFirQuickDocMultiplatformTest: KotlinLightMultiplatformCodeInsightFixtureTestCase() {

    fun getDoc(): String? {
        val target =
            IdeDocumentationTargetProvider.getInstance(project).documentationTargets(editor, file, editor.caretModel.offset).firstOrNull()
                ?: return null
        return computeDocumentationBlocking(target.createPointer())?.html
    }

    fun doTest(path: String) {
        val virtualFile = myFixture.configureMultiPlatformModuleStructure(path).mainFile
        require(virtualFile != null)

        myFixture.configureFromExistingVirtualFile(virtualFile)
        val element = myFixture.getFile().findElementAt(myFixture.editor.caretModel.offset)
        assertNotNull("Can't find element at caret in file: $path", element)
        var info = getDoc()
        if (info != null) {
            info = StringUtil.convertLineSeparators(info)
        }

        val testDataFile = File(path)
        val textData = FileUtil.loadFile(testDataFile, true)
        val directives =
            InTextDirectivesUtils.findLinesWithPrefixesRemoved(textData, false, true, "INFO:")
        if (directives.isEmpty()) {
            throw FileComparisonFailedError(
                "'// INFO:' directive was expected",
                textData,
                "$textData\n\n//INFO: $info",
                testDataFile.absolutePath
            )
        }
        else {
            val expectedInfo = directives.joinToString("\n")
            if (expectedInfo != info) {
                wrapToFileComparisonFailure(info, path, textData)
            }
        }
    }
}
