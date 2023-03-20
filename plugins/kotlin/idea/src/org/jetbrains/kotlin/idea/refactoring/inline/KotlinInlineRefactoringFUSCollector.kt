// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.internal.statistic.collectors.fus.LangCustomRuleValidator
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import org.jetbrains.kotlin.idea.util.isAnonymousFunction
import org.jetbrains.kotlin.psi.*

class KotlinInlineRefactoringFUSCollector : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup = GROUP

    companion object {
        val GROUP = EventLogGroup("kotlin.ide.refactoring.inline", 4)

        private val elementType = EventFields.Enum("element_type", ElementType::class.java)
        private val languageFrom = EventFields.StringValidatedByCustomRule("language_from", LangCustomRuleValidator::class.java)
        private val languageTo = EventFields.StringValidatedByCustomRule("language_to", LangCustomRuleValidator::class.java)
        private val isCrossLang = EventFields.Boolean("is_cross_lang")
        private val pluginInfo = EventFields.PluginInfo
        private val event = GROUP.registerVarargEvent(
            "Launched",
            elementType,
            languageFrom,
            languageTo,
            isCrossLang,
            pluginInfo,
        )

        private fun log(elementType: ElementType, languageFrom: Language, languageTo: Language, isCrossLanguage: Boolean) = event.log(
            this.elementType.with(elementType),
            this.languageFrom.with(languageFrom.id),
            this.languageTo.with(languageTo.id),
            this.isCrossLang.with(isCrossLanguage),
            this.pluginInfo.with(getPluginInfoById(KotlinIdePlugin.id)),
        )

        fun log(elementFrom: PsiElement, languageTo: Language, isCrossLanguage: Boolean) = log(
            elementType = if (elementFrom is KtElement) elementFrom.type else elementFrom.type,
            languageFrom = elementFrom.language,
            languageTo = languageTo,
            isCrossLanguage = isCrossLanguage,
        )

        private val KtElement.type: ElementType
            get() = when (this) {
                is KtConstructor<*> -> ElementType.CONSTRUCTOR
                is KtFunctionLiteral -> ElementType.LAMBDA_EXPRESSION
                is KtNamedFunction -> if (isAnonymousFunction) ElementType.ANONYMOUS_FUNCTION else ElementType.FUNCTION
                is KtTypeAlias -> ElementType.TYPE_ALIAS
                is KtProperty -> if (isLocal) ElementType.LOCAL_VARIABLE else ElementType.PROPERTY
                else -> ElementType.UNKNOWN
            }

        private val PsiElement.type: ElementType
            get() {
                if (language != JavaLanguage.INSTANCE) return ElementType.UNKNOWN
                return when (this) {
                    is PsiMethod -> if (isConstructor) ElementType.CONSTRUCTOR else ElementType.FUNCTION
                    is PsiField -> ElementType.PROPERTY
                    else -> ElementType.UNKNOWN
                }
            }
    }

    enum class ElementType {
        FUNCTION,
        ANONYMOUS_FUNCTION,
        LAMBDA_EXPRESSION,
        CONSTRUCTOR,
        TYPE_ALIAS,
        PROPERTY,
        LOCAL_VARIABLE,
        UNKNOWN,
    }
}
