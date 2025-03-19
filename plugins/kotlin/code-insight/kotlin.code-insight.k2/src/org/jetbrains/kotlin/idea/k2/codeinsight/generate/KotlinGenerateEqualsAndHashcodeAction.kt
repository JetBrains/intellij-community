// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.generate

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.idea.actions.generate.createMemberInfo
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.psi.isInlineOrValue
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.insertMembersAfterAndReformat
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.GenerateEqualsAndHashCodeUtils.confirmMemberRewrite
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.GenerateEqualsAndHashCodeUtils.findEqualsMethodForClass
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.GenerateEqualsAndHashCodeUtils.findHashCodeMethodForClass
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.GenerateEqualsAndHashCodeUtils.generateEquals
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.GenerateEqualsAndHashCodeUtils.generateHashCode
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.GenerateEqualsAndHashCodeUtils.getPropertiesToUseInGeneratedMember
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.utils.addToStdlib.lastIsInstanceOrNull
import org.jetbrains.kotlin.utils.keysToMap

class Info(
    val klass: KtClass,
    val variablesForEquals: List<KtNamedDeclaration>,
    val variablesForHashCode: List<KtNamedDeclaration>,
    val equalsInClass: KtNamedFunction?,
    val hashCodeInClass: KtNamedFunction?
)

class KotlinGenerateEqualsAndHashcodeAction : KotlinGenerateMemberActionBase<Info>() {
    companion object {
        const val BASE_PARAM_NAME = "baseParamName"

        const val SUPER_HAS_EQUALS = "superHasEquals"
        const val SUPER_HAS_HASHCODE = "superHasHashCode"

        const val SUPER_PARAM_NAME = "superParamName"

        const val CHECK_PARAMETER_WITH_INSTANCEOF = "checkParameterWithInstanceof"
    }

    override fun isValidForClass(targetClass: KtClassOrObject): Boolean {
        return targetClass is KtClass
                && targetClass !is KtEnumEntry
                && !targetClass.isEnum()
                && !targetClass.isAnnotation()
                && !targetClass.isInterface()
                && !targetClass.isInlineOrValue()
    }

    override fun prepareMembersInfo(klass: KtClassOrObject, project: Project, editor: Editor): Info? {
        if (klass !is KtClass) return null
        val (preInfo, equalsMembers, hashMembers) = analyzeInModalWindow(klass, KotlinBundle.message("fix.change.signature.prepare")) {
            val classSymbol = klass.symbol as? KaClassSymbol ?: return@analyzeInModalWindow null
            val properties = getPropertiesToUseInGeneratedMember(klass)

            val equalsMethodForClass = findEqualsMethodForClass(classSymbol)
            val hashCodeMethodForClass = findHashCodeMethodForClass(classSymbol)

            Triple(
                Info(
                klass,
                properties,
                properties,
                (equalsMethodForClass?.psi as? KtNamedFunction)?.takeIf { equalsMethodForClass.containingSymbol == classSymbol },
                (hashCodeMethodForClass?.psi as? KtNamedFunction)?.takeIf { hashCodeMethodForClass.containingSymbol == classSymbol }
            ), properties.map { createMemberInfo(it) }, LinkedHashMap(properties.keysToMap<KtNamedDeclaration, KotlinMemberInfo> { createMemberInfo(it) }))
        } ?: return null

        var equalsInClass = preInfo.equalsInClass
        var hashCodeInClass = preInfo.hashCodeInClass
        if (preInfo.variablesForEquals.isEmpty() || ApplicationManager.getApplication().isUnitTestMode()) {
            return Info(klass, preInfo.variablesForEquals, preInfo.variablesForHashCode, equalsInClass, hashCodeInClass)
        }

        if (equalsInClass != null && hashCodeInClass != null) {
            if (!confirmMemberRewrite(
                    klass,
                    KotlinBundle.message("generate.equals.and.hashcode.fix.text"),
                    equalsInClass,
                    hashCodeInClass
                )
            ) return null

            runWriteAction {
                equalsInClass?.delete()
                hashCodeInClass?.delete()
                equalsInClass = null
                hashCodeInClass = null

            }
        }

        return with( KotlinGenerateEqualsAndHashCodeWizard(project, klass, preInfo.variablesForEquals, equalsInClass == null, hashCodeInClass == null, equalsMembers, hashMembers)) {
            if (!klass.hasExpectModifier() && !showAndGet()) return null

            Info(
                klass,
                getPropertiesForEquals(),
                getPropertiesForHashCode(),
                equalsInClass,
                hashCodeInClass
            )
        }
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
