// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.generate

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateEqualsAndHashcodeAction
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils

abstract class AbstractGenerateHashCodeAndEqualsActionTest : AbstractCodeInsightActionTest() {
    override fun createAction(fileText: String) = KotlinGenerateEqualsAndHashcodeAction()

    override fun doTest(path: String) {
        val fileText = FileUtil.loadFile(dataFile(), true)

        val codeInsightSettings = CodeInsightSettings.getInstance()
        val useInstanceOfOnEqualsParameterOld = codeInsightSettings.USE_INSTANCEOF_ON_EQUALS_PARAMETER

        try {
            codeInsightSettings.USE_INSTANCEOF_ON_EQUALS_PARAMETER = InTextDirectivesUtils.isDirectiveDefined(fileText, "// USE_IS_CHECK")
            super.doTest(path)
        } finally {
            codeInsightSettings.USE_INSTANCEOF_ON_EQUALS_PARAMETER = useInstanceOfOnEqualsParameterOld
        }
    }
}
