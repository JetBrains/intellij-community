// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.generate

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateToStringAction
import org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateToStringAction.Generator
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

abstract class AbstractGenerateToStringActionTest : AbstractCodeInsightActionTest() {
    override fun createAction(fileText: String) = KotlinGenerateToStringAction()

    override fun testAction(action: AnAction, forced: Boolean): Presentation {
        val fileText = file.text
        val generator = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// GENERATOR: ")?.let { Generator.valueOf(it) }
        val generateSuperCall = InTextDirectivesUtils.isDirectiveDefined(fileText, "// GENERATE_SUPER_CALL")
        val klass = file.findElementAt(editor.caretModel.offset)?.getStrictParentOfType<KtClassOrObject>()
        try {
            with(KotlinGenerateToStringAction) {
                klass?.adjuster = { it.copy(generateSuperCall = generateSuperCall, generator = generator ?: it.generator) }
            }
            return super.testAction(action, forced)
        } finally {
            with(KotlinGenerateToStringAction) { klass?.adjuster = null }
        }
    }
}
