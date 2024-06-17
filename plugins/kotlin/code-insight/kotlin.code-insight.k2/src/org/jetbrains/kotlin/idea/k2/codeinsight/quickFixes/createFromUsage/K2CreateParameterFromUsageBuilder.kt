// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.convertToClass
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2ExtractableSubstringInfo
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.approximateWithResolvableType
import org.jetbrains.kotlin.idea.k2.refactoring.introduceParameter.KotlinFirIntroduceParameterHandler
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateParameterUtil
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.idea.refactoring.introduce.extractableSubstringInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter.IntroduceParameterDescriptor
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter.KotlinIntroduceParameterHelper
import org.jetbrains.kotlin.idea.refactoring.introduce.substringContextOrThis
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getAssignmentByLHS
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElement

object K2CreateParameterFromUsageBuilder {
    fun generateCreateParameterAction(element: KtElement): IntentionAction? {
        val refExpr = element.findParentOfType<KtNameReferenceExpression>(strict = false) ?: return null
        if (refExpr.getQualifiedElement() != refExpr) return null
        if (refExpr.getParentOfTypeAndBranch<KtCallableReferenceExpression> { callableReference } != null) return null

        val pair = getContainer(refExpr)
        val container = pair.first as? KtNamedDeclaration ?: return null
        return CreateParameterFromUsageAction(refExpr, refExpr.getReferencedName(), pair.second, container)
    }

    private fun getContainer(refExpr: KtExpression): Pair<PsiElement?, CreateParameterUtil.ValVar> {
        val varExpected = refExpr.getAssignmentByLHS() != null
        return CreateParameterUtil.chooseContainerPreferringClass(refExpr, varExpected)
    }

    fun generateCreateParameterActionForNamedParameterNotFound(arg: KtValueArgument): IntentionAction? {
        val name = arg.getArgumentName()?.text?: return null
        val expression = arg.getArgumentExpression()?: return null
        analyze (arg) {
            val callExpression = (arg.parent?.parent as? KtCallElement) ?: return null
            val call = callExpression.resolveCallOld()?.singleFunctionCallOrNull() ?: return null
            val namedDeclaration = call.partiallyAppliedSymbol.symbol.psi as? KtNamedDeclaration ?: return null
            val namedDeclClass = if (namedDeclaration is KtConstructor<*>) namedDeclaration.getContainingClassOrObject() else namedDeclaration
            val valVar = if (namedDeclClass is KtClass && (namedDeclClass.isData() || namedDeclClass.isAnnotation()))
                CreateParameterUtil.ValVar.VAL else CreateParameterUtil.ValVar.NONE
            return CreateParameterFromUsageAction(expression, name, valVar, namedDeclaration)
        }
    }
    fun generateCreateParameterActionForComponentFunctionMissing(arg: PsiElement, destructingType: KtType): IntentionAction? {
        val decl = arg.findParentOfType<KtDestructuringDeclaration>(strict = false) ?: return null
        val lastEntry = decl.entries.lastOrNull()
        val name = lastEntry?.name?:return null
        analyze(decl) {
            val container = destructingType.convertToClass() ?: return null
            val classParamCount = container.primaryConstructor?.getValueParameters()?.size ?: return null
            if (classParamCount != decl.entries.size-1) return null // can add only one parameter at a time
            return CreateParameterFromUsageAction(lastEntry, name, CreateParameterUtil.ValVar.VAL, container)
        }
    }

    internal class CreateParameterFromUsageAction(refExpr: KtExpression, private val propertyName: String, private val valVar: CreateParameterUtil.ValVar, container: KtNamedDeclaration) : IntentionAction {
        private val originalExprPointer: SmartPsiElementPointer<KtExpression> = SmartPointerManager.createPointer(refExpr)
        private val containerPointer: SmartPsiElementPointer<KtNamedDeclaration> = SmartPointerManager.createPointer(container)
        override fun getText(): String =
            if (valVar == CreateParameterUtil.ValVar.NONE)
                KotlinBundle.message("create.parameter.0", propertyName)
            else
                KotlinBundle.message("create.property.0.as.constructor.parameter", propertyName)

        override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
            val container = containerPointer.element ?: return
            val originalExpression = originalExprPointer.element ?: return
            if (!ReadonlyStatusHandler.ensureFilesWritable(project, PsiUtil.getVirtualFile(container))) {
                return
            }
            runChangeSignature(project, editor!!, container, valVar, propertyName, originalExpression)
        }

        override fun startInWriteAction(): Boolean = false
        override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
            return originalExprPointer.element != null
        }
        override fun getFamilyName(): String = KotlinBundle.message("fix.create.from.usage.family")

        context(KaSession)
        private fun getExpectedType(expression: KtExpression): KtType {
            if (expression is KtDestructuringDeclarationEntry) {
                val type = expression.getReturnKtType()
                return if (type is KtErrorType) builtinTypes.ANY else type
            }
            val physicalExpression = expression.substringContextOrThis
            val type = if (physicalExpression is KtProperty && physicalExpression.isLocal) {
                physicalExpression.getReturnKtType()
            } else {
                (expression.extractableSubstringInfo as? K2ExtractableSubstringInfo)?.guessLiteralType() ?: physicalExpression.getKtType()
            }
            val approximatedType = approximateWithResolvableType(type, physicalExpression)
            if (approximatedType!=null && approximatedType != builtinTypes.UNIT) { return approximatedType }

            expression.getExpectedType()?.let { return it }
            val binaryExpression = expression.getAssignmentByLHS()
            val right = binaryExpression?.right
            right?.getKtType()?.let { return it }
            right?.getExpectedType()?.let { return it }
            (expression.parent as? KtDeclaration)?.getReturnKtType()?.let { return it }
            return builtinTypes.ANY
        }

        private fun runChangeSignature(
            project: Project,
            editor: Editor,
            container: KtNamedDeclaration,
            valVar: CreateParameterUtil.ValVar,
            name: String,
            originalExpression: KtExpression
        ) {
            val helper = object : KotlinIntroduceParameterHelper<KtNamedDeclaration> {
                override fun configure(descriptor: IntroduceParameterDescriptor<KtNamedDeclaration>): IntroduceParameterDescriptor<KtNamedDeclaration> {
                    // let generator know whether to insert var/val
                    descriptor.valVar = when(valVar) {
                        CreateParameterUtil.ValVar.VAL -> KotlinValVar.Val
                        CreateParameterUtil.ValVar.VAR -> KotlinValVar.Var
                        CreateParameterUtil.ValVar.NONE -> KotlinValVar.None
                    }
                    return descriptor
                }
            }
            KotlinFirIntroduceParameterHandler(helper).addParameter(project, editor, originalExpression, container, { getExpectedType(originalExpression) }, { _ -> listOf(name) })
        }
    }
}
