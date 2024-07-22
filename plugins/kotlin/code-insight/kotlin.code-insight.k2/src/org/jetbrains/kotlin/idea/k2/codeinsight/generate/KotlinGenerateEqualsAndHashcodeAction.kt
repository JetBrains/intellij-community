// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.generate

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.idea.actions.generate.createMemberInfo
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.psi.isInlineOrValue
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.GenerateEqualsAndHashCodeUtils.findEqualsMethodForClass
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.GenerateEqualsAndHashCodeUtils.findHashCodeMethodForClass
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.GenerateEqualsAndHashCodeUtils.getPropertiesToUseInGeneratedMember
import org.jetbrains.kotlin.idea.core.insertMembersAfterAndReformat
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.utils.addToStdlib.lastIsInstanceOrNull
import org.jetbrains.kotlin.utils.keysToMap

class KotlinGenerateEqualsAndHashcodeAction : KotlinGenerateMemberActionBase<KotlinGenerateEqualsAndHashcodeAction.Info>() {
    companion object {
        const val BASE_PARAM_NAME = "baseParamName"

        const val SUPER_HAS_EQUALS = "superHasEquals"
        const val SUPER_HAS_HASHCODE = "superHasHashCode"

        const val CHECK_PARAMETER_WITH_INSTANCEOF = "checkParameterWithInstanceof"
    }

    class Info(
        val needEquals: Boolean,
        val needHashCode: Boolean,
        val klass: KtClass,
        val variablesForEquals: List<KtNamedDeclaration>,
        val variablesForHashCode: List<KtNamedDeclaration>
    )

    override fun isValidForClass(targetClass: KtClassOrObject): Boolean {
        return targetClass is KtClass
                && targetClass !is KtEnumEntry
                && !targetClass.isEnum()
                && !targetClass.isAnnotation()
                && !targetClass.isInterface()
                && !targetClass.isInlineOrValue()
    }

    override fun prepareMembersInfo(klass: KtClassOrObject, project: Project, editor: Editor): Info? {
        val asClass = klass as? KtClass ?: return null
        return prepareMembersInfo(asClass, project, true)
    }

    fun prepareMembersInfo(klass: KtClass, project: Project, askDetails: Boolean): Info? {
        val (preInfo, toMemberInfo) = analyzeInModalWindow(klass, KotlinBundle.message("fix.change.signature.prepare")) {
            val classSymbol = klass.symbol as? KaClassSymbol ?: return@analyzeInModalWindow null
            val properties = getPropertiesToUseInGeneratedMember(klass)

            var needEquals = findEqualsMethodForClass(classSymbol)?.containingSymbol != classSymbol
            var needHashCode = findHashCodeMethodForClass(classSymbol)?.containingSymbol != classSymbol

            Info(
                needEquals,
                needHashCode,
                klass,
                properties,
                properties
            ) to LinkedHashMap(properties.keysToMap<KtNamedDeclaration, KotlinMemberInfo> { createMemberInfo(it) })
        } ?: return null

        if (preInfo.variablesForEquals.isEmpty() || ApplicationManager.getApplication().isUnitTestMode() || !askDetails) {
            return Info(preInfo.needEquals, preInfo.needHashCode, klass, preInfo.variablesForEquals, preInfo.variablesForHashCode)
        }

        return with( KotlinGenerateEqualsAndHashCodeWizard(project, klass, preInfo.variablesForEquals, preInfo.needEquals, preInfo.needHashCode, toMemberInfo.values.toList(), toMemberInfo)) {
            if (!klass.hasExpectModifier() && !showAndGet()) return null

            Info(preInfo.needEquals,
                 preInfo.needHashCode,
                 klass,
                 getPropertiesForEquals(),
                 getPropertiesForHashCode())
        }
    }

    context(KaSession)
    private fun generateEquals(info: Info): KtNamedFunction? {
        if (!info.needEquals) return null

        val klass = info.klass

        val contextMap = mutableMapOf<String, Any?>()

        contextMap[BASE_PARAM_NAME] = "other"

        val equalsFunction = findEqualsMethodForClass(klass.symbol as KaClassSymbol)

        contextMap[SUPER_HAS_EQUALS] =equalsFunction != null && (equalsFunction.containingSymbol as? KaClassSymbol)?.classId != StandardClassIds.Any
        contextMap[CHECK_PARAMETER_WITH_INSTANCEOF] = CodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER

        val methodText = VelocityGeneratorHelper
            .velocityGenerateCode(klass, info.variablesForEquals, contextMap,
                                  KotlinEqualsHashCodeTemplatesManager.getInstance().defaultEqualsTemplate.template, false) ?: return null


        return KtPsiFactory.contextual(klass).createFunction(methodText)
    }

    context(KaSession)
    fun generateHashCode(info: Info): KtNamedFunction? {
        if (!info.needHashCode) return null

        val klass = info.klass

        val contextMap = mutableMapOf<String, Any?>()
        val hashCodeFunction = findHashCodeMethodForClass(klass.symbol as KaClassSymbol)
        contextMap[SUPER_HAS_HASHCODE] = hashCodeFunction != null && (hashCodeFunction.containingSymbol as? KaClassSymbol)?.classId != StandardClassIds.Any

        val methodText = VelocityGeneratorHelper
                             .velocityGenerateCode(klass, info.variablesForHashCode,
                                                   contextMap, KotlinEqualsHashCodeTemplatesManager.getInstance().defaultHashcodeTemplate.template, false) ?: return null


        return KtPsiFactory.contextual(klass).createFunction(methodText)
    }

    override fun generateMembers(project: Project, editor: Editor, info: Info): List<KtDeclaration> {
        val targetClass = info.klass
        val prototypes = ArrayList<KtDeclaration>(2)

        analyzeInModalWindow(targetClass, KotlinBundle.message("fix.change.signature.prepare")) {
            prototypes.addIfNotNull(generateEquals(info))
            prototypes.addIfNotNull(generateHashCode(info))
        }

        val anchor = with(targetClass.declarations) { lastIsInstanceOrNull<KtNamedFunction>() ?: lastOrNull() }
        var members: List<KtDeclaration>? = null
        project.executeWriteCommand(commandName) { members = insertMembersAfterAndReformat(editor, targetClass, prototypes, anchor) }
        return members ?: emptyList()
    }
}
