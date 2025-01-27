// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.generate

import com.intellij.codeInsight.actions.CodeInsightAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.codeInsight.generate.AbstractCodeInsightActionTest
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.KotlinGenerateToStringAction
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

abstract class AbstractFirGenerateToStringActionTest : AbstractCodeInsightActionTest() {
    override fun createAction(fileText: String): CodeInsightAction {
        return KotlinGenerateToStringAction()
    }

    override fun testAction(action: AnAction): Presentation {
        val fileText = file.text
        val generator = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// GENERATOR: ")
        val generateSuperCall = InTextDirectivesUtils.isDirectiveDefined(fileText, "// GENERATE_SUPER_CALL")
        val template = when {
            generator == "MULTIPLE_TEMPLATES" && generateSuperCall -> KotlinBundle.message("action.generate.tostring.template.multiple.with.super")
            generator == "MULTIPLE_TEMPLATES" -> KotlinBundle.message("action.generate.tostring.template.multiple")
            generator == "SINGLE_TEMPLATE" && generateSuperCall -> KotlinBundle.message("action.generate.tostring.template.single.with.super")
            else -> KotlinBundle.message("action.generate.tostring.template.single")
        }
        val klass = file.findElementAt(editor.caretModel.offset)?.getStrictParentOfType<KtClassOrObject>()
        try {
            with(KotlinGenerateToStringAction) {
                klass?.adjuster = { it.copy(templateName = template) }
            }
            return super.testAction(action)
        } finally {
            with(KotlinGenerateToStringAction) { klass?.adjuster = null }
        }
    }
}