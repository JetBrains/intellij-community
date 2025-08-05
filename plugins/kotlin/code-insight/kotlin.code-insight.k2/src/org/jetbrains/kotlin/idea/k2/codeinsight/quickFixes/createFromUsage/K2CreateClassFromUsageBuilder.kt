package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage // Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.actions.ExpectedParameter
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.findParentOfType
import com.intellij.util.text.UniqueNameGenerator
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.isAnyType
import org.jetbrains.kotlin.analysis.api.components.isUnitType
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.idea.codeinsight.utils.*
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableActionTextBuilder.renderCandidatesOfParameterTypes
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.computeExpectedParams
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.convertToClass
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.getExpectedKotlinType
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.toKtTypeWithNullability
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.ClassKind
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateClassUtil.checkClassName
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateClassUtil.isQualifierExpected
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.utils.ifEmpty

object K2CreateClassFromUsageBuilder {
    fun generateCreateClassActions(element: KtElement): List<IntentionAction> {
        val refExpr = element.findParentOfType<KtNameReferenceExpression>(strict = false) ?: return listOf()
        if (refExpr.getParentOfTypeAndBranch<KtCallableReferenceExpression> { callableReference } != null) return listOf()

        analyze(refExpr) {
            val expectedType: ExpectedKotlinType? = refExpr.getExpectedKotlinType()
            val superClass: KtClass? = expectedType?.kaType?.convertToClass()

            val superClassSymbol = superClass?.classSymbol ?: (expectedType?.kaType as? KaClassType)?.symbol as? KaClassSymbol
            val superClassName:String? = superClass?.name
            val isAny = superClassName == StandardClassIds.Any.shortClassName.asString()
            val returnTypeString: String = if (superClass == null || superClassName == null || isAny) ""
                else if (superClass.isInterface()) ": $superClassName" else ": $superClassName()"

            val (classKinds, targetParents, inner) = getPossibleClassKindsAndParents(refExpr) ?: return listOf()
            return classKinds.mapNotNull { kind ->
                val canCreate = if (superClassSymbol?.classKind == KaClassKind.ENUM_CLASS) {
                    // can create enum constant in an enum
                    kind == ClassKind.ENUM_ENTRY
                }
                else {
                    superClassSymbol?.modality != KaSymbolModality.FINAL
                }
                if (canCreate) {
                    val applicableParents = targetParents
                        .filter {
                            if (kind == ClassKind.OBJECT && it is KtClass) {
                                if (it.isInner() || it.isLocal) false
                                else if (it.isEnum() && it == superClass) false // can't create `object` in enum of enum type
                                else true
                            } else true
                        }
                        .map { it.createSmartPointer() }
                    val isAnnotation = kind == ClassKind.ANNOTATION_CLASS
                    val paramListRendered = renderParamList(isAnnotation, refExpr)
                    val open = isInsideExtendsList(refExpr)
                    val name = refExpr.getReferencedName()
                    if (kind == ClassKind.ANNOTATION_CLASS || name.checkClassName()) {
                        CreateKotlinClassAction(
                            refExpr.createSmartPointer(),
                            kind,
                            applicableParents,
                            inner,
                            open,
                            name,
                            superClassName,
                            paramListRendered.renderedParamList,
                            paramListRendered.candidateList,
                            returnTypeString,
                            paramListRendered.primaryConstructorVisibilityModifier
                        )
                    }
                    else {
                        null
                    }
                }
                else {
                    null
                }
            }
        }
    }

    private fun isInsideExtendsList(element: PsiElement): Boolean {
        val superTypeList = PsiTreeUtil.getParentOfType(element, KtSuperTypeList::class.java, false, KtTypeArgumentList::class.java)
        return superTypeList != null
    }
    private fun isInExtendsClauseOfAnnotationOrEnumOrInline(element: KtExpression): Boolean {
        val superTypeList = PsiTreeUtil.getParentOfType(element, KtSuperTypeList::class.java, false, KtTypeArgumentList::class.java) ?: return false

        val ktClass = superTypeList.findParentOfType<KtClass>(strict = false) ?: return false
        return ktClass.isAnnotation() || ktClass.isEnum() || ktClass.isInline()
    }

    internal class ParamListRenderResult(val renderedParamList: String, val candidateList: List<CreateKotlinCallableAction.ParamCandidate>, val primaryConstructorVisibilityModifier: String?)
    context(_: KaSession)
    private fun renderParamList(isAnnotation:Boolean, refExpr: KtNameReferenceExpression): ParamListRenderResult {
        val renderedParameters: String
        val shouldParenthesize: Boolean
        val prefix = if (isAnnotation) "val " else ""
        val superTypeCallEntry = refExpr.findParentOfType<KtCallElement>(false)?:return ParamListRenderResult("", listOf(), null)
        val expectedParams = computeExpectedParams(superTypeCallEntry, isAnnotation)
        val candidateList = renderCandidatesOfParameterTypes(expectedParams, refExpr)
        // find params from the ref parameters, e.g.: `class F: Foo(1,"2")`
        val uniqueNameGenerator = UniqueNameGenerator()
        renderedParameters = candidateList.joinToString(", ") { prefix + uniqueNameGenerator.generateUniqueName(it.names.first()) + ": " + it.renderedTypes.first() }
        shouldParenthesize = expectedParams.isNotEmpty()
        val renderedParamList = if (shouldParenthesize)
            "($renderedParameters)"
        else
            renderedParameters

        val primaryConstructorVisibilityModifier = expectedParams.fold<ExpectedParameter, String?>(null) { curVisibility:String?, param:ExpectedParameter ->
            if (curVisibility == KtTokens.PRIVATE_KEYWORD.value || param.expectedTypes.any { it.toKtTypeWithNullability(refExpr)?.convertToClass()?.isPrivate() == true }) {
                KtTokens.PRIVATE_KEYWORD.value
            }
            else if (curVisibility == KtTokens.INTERNAL_KEYWORD.value || param.expectedTypes.any { it.toKtTypeWithNullability(refExpr)?.convertToClass()?.visibilityModifierTypeOrDefault()?.toVisibility() == Visibilities.Internal }) {
                KtTokens.INTERNAL_KEYWORD.value
            }
            else {
                null
            }
        }
        return ParamListRenderResult(renderedParamList, candidateList, primaryConstructorVisibilityModifier)
    }

    data class PossibleParentClass(val classKinds: List<ClassKind>, val targetParents: List<PsiElement>, val inner:Boolean)

    context(_: KaSession)
    private fun getPossibleClassKindsAndParents(element: KtSimpleNameExpression): PossibleParentClass? {
        val name = element.getReferencedName()

        val fullCallExpr = element.parent.let {
            when {
                it is KtCallExpression && it.calleeExpression == element -> it
                it is KtQualifiedExpression && it.selectorExpression == element -> it
                else -> element
            }
        }

        if (fullCallExpr.getAssignmentByLHS() != null) return null

        val inImport = element.getNonStrictParentOfType<KtImportDirective>() != null
        if (inImport || isQualifierExpected(element)) {
            val receiverSelector =
                (fullCallExpr as? KtQualifiedExpression)?.receiverExpression?.getQualifiedElementSelector() as? KtReferenceExpression
            val targets = getTargetParentsByQualifier(element, receiverSelector)
            val targetParents = targets.first.ifEmpty { return null }

            if (!name.checkClassName()) return null

            val classKinds = ClassKind.entries.filter {
                when (it) {
                    ClassKind.ANNOTATION_CLASS -> inImport
                    ClassKind.ENUM_ENTRY -> inImport && targetParents.any { parent -> isEnum(parent) }
                    else -> true
                }
            }
            return PossibleParentClass(classKinds, targetParents, targets.second)
        }

        val fullParent = fullCallExpr.parent
        val receiverExpression = if (fullCallExpr is KtQualifiedExpression) fullCallExpr.receiverExpression
                             else if (fullParent is KtUserType) fullParent.qualifier?.referenceExpression
                             else if (fullParent is KtQualifiedExpression) fullParent.receiverExpression
                             else null
        if (receiverExpression is KtConstantExpression) return null // filter out `2.Foo()`
        val targets = getTargetParentsByQualifier(fullCallExpr, receiverExpression)
        val targetParents = targets.first
        val parent = element.parent
        val typeReference = parent.getNonStrictParentOfType<KtTypeReference>()
        if (parent is KtClassLiteralExpression && parent.receiverExpression == element || typeReference != null || parent is KtCallExpression) {
            val hasTypeArguments = ((fullParent as? KtUserType)?.getTypeArgumentsAsTypes() ?: (parent as? KtCallExpression)?.typeArguments ?: emptyList()).isNotEmpty()
            val isQualifier = (fullParent.parent as? KtUserType)?.qualifier == fullParent
            val inTypeBound = typeReference != null && (
              (typeReference.parent as? KtTypeParameter)?.extendsBound == typeReference ||
              (typeReference.parent as? KtTypeConstraint)?.boundTypeReference == typeReference)
            val possibleKinds = ClassKind.entries.filter {
                                        when (it) {
                                            ClassKind.OBJECT -> !hasTypeArguments && isQualifier || parent is KtClassLiteralExpression
                                            ClassKind.ANNOTATION_CLASS -> !hasTypeArguments && !isQualifier && !inTypeBound
                                            ClassKind.ENUM_ENTRY -> false
                                            ClassKind.DEFAULT -> false
                                            ClassKind.ENUM_CLASS -> !hasTypeArguments && !inTypeBound
                                            ClassKind.PLAIN_CLASS -> !isInExtendsClauseOfAnnotationOrEnumOrInline(fullCallExpr) // annotation/enum/inline class can't extend a class
                                            else -> true
                                        }
                                    }

            return PossibleParentClass(possibleKinds, targetParents, targets.second)
        }

        analyze (element) {
            val isReceiverAccepted = receiverExpression == null ||
                    receiverExpression is KtNameReferenceExpression &&
                    receiverExpression.resolveExpression()?.psi.let { it is KtClass || it is PsiPackage }
            if (!isReceiverAccepted) {
                // for `expression.Foo()` we can't create object nor enum
                return null
            }
            val expectedType: KaType? = fullCallExpr.expectedType
            val isObjectAccepted = targetParents.any { targetParent -> isClassKindAccepted(expectedType, targetParent, ClassKind.OBJECT) && (expectedType == null || !isEnum(targetParent))}
            val isEnumAccepted = targetParents.any { targetParent -> isEnum(targetParent) && isClassKindAccepted(expectedType, targetParent, ClassKind.ENUM_ENTRY) }
            val classKinds = listOfNotNull(if (isObjectAccepted) ClassKind.OBJECT else null, if (isEnumAccepted) ClassKind.ENUM_ENTRY else null)
            return PossibleParentClass(classKinds, targetParents, false)
        }
    }

    private fun isEnum(element: PsiElement): Boolean {
        return when (element) {
            is KtClass -> element.isEnum()
            is PsiClass -> element.isEnum
            else -> false
        }
    }

    // return list of parents, inner=true if should create inner class
    context(_: KaSession)
    private fun getTargetParentsByQualifier(
        element: KtElement,
        receiverExpression: KtExpression?
    ): Pair<List<PsiElement>, Boolean> {
        val qualifier = receiverExpression?.resolveExpression()
        val file = element.containingKtFile
        val project = file.project
        var targetParents:List<PsiElement> = emptyList()
        var inner = false
        if (qualifier == null) {
            targetParents = element.parents.filterIsInstance<KtClassOrObject>().toList() + file
        }
        else if (qualifier is KaClassSymbol) {
            targetParents = listOfNotNull(qualifier.psi)
        }
        else if (qualifier is KaPackageSymbol) {
            if (qualifier.fqName == file.packageFqName) {
                targetParents = listOf(file)
            }
            else {
                targetParents = listOfNotNull(JavaPsiFacade.getInstance(project).findPackage(qualifier.fqName.asString()))
            }
        }
        else if (qualifier is KaCallableSymbol) {
            targetParents = listOfNotNull(qualifier.returnType.convertToClass())
            inner = true
        }
        if (receiverExpression is KtThisExpression) {
            // expression `this.Foo()` must denote inner class creation
            inner = true
        }
        return Pair(targetParents.filter { it.canRefactorElement() }, inner)
    }

    context (_: KaSession)
    private fun isClassKindAccepted(expectedType: KaType?, containingDeclaration: PsiElement, classKind: ClassKind): Boolean {
        if (expectedType == null || expectedType.isAnyType) {
            return true
        }

        val canHaveSubtypes = isInheritable(expectedType) || !(expectedType.containsStarProjections()) || expectedType.isUnitType
        val isEnum = expectedType is KaClassType && expectedType.isEnum()

        if (!(canHaveSubtypes || isEnum) || expectedType is KaTypeParameterType) return false

        return when (classKind) {
            ClassKind.ENUM_ENTRY -> isEnum && containingDeclaration == expectedType.convertToClass()
            else -> canHaveSubtypes
        }
    }

    context(_: KaSession)
    private fun isInheritable(type: KaType): Boolean {
        return type.convertToClass()?.isInheritable() == true
    }
}
