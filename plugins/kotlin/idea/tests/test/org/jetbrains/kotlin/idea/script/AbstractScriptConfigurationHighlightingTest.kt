// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.script

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.psi.KtFile
import org.junit.ComparisonFailure

abstract class AbstractScriptConfigurationHighlightingTest : AbstractScriptConfigurationTest() {
    fun doTest(unused: String) {
        configureScriptFile(testDataFile())
        assertNotNull(ScriptConfigurationManager.getInstance(project).getConfiguration(myFile as KtFile))

        // Highlight references at caret
        HighlightUsagesHandler.invoke(project, editor, myFile)

        checkHighlighting(
            editor,
            InTextDirectivesUtils.isDirectiveDefined(file.text, "// CHECK_WARNINGS"),
            InTextDirectivesUtils.isDirectiveDefined(file.text, "// CHECK_INFOS")
        )
    }

    fun doComplexTest(unused: String) {
        configureScriptFile(testDataFile())
        assertThrows(ComparisonFailure::class.java) {
            checkHighlighting(editor, false, false)
        }

        ScriptConfigurationManager.updateScriptDependenciesSynchronously(myFile)
        checkHighlighting(editor, false, false)
    }
}