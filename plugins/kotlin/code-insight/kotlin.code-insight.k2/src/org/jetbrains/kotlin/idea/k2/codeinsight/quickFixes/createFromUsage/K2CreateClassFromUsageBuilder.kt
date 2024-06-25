// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.idea.codeinsight.utils.isEnum
import org.jetbrains.kotlin.idea.codeinsight.utils.isInheritable
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.ExpectedKotlinType
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.convertToClass
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.getExpectedKotlinType
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.resolveExpression
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.ClassKind
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateClassUtil.checkClassName
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateClassUtil.getFullCallExpression
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateClassUtil.isQualifierExpected
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.utils.ifEmpty

object K2CreateClassFromUsageBuilder {
    fun generateCreateClassActions(element: KtElement): List<IntentionAction> {
        //if (true) return listOf()
        val refExpr = element.findParentOfType<KtNameReferenceExpression>(strict = false) ?: return listOf()
        //if (refExpr.getQualifiedElement() != refExpr) return listOf()
        if (refExpr.getParentOfTypeAndBranch<KtCallableReferenceExpression> { callableReference } != null) return listOf()
        //val targetParents = mutableListOf<PsiElement>()
        val qualifiedElement = refExpr.getQualifiedElement()

        var expectedType: ExpectedKotlinType?
        var superClassName:String?
        var paramList: String?
        var returnTypeString: String = ""
        var superClass: KtClass?
        analyze(refExpr) {
            expectedType = refExpr.getExpectedKotlinType()
            superClass = expectedType?.ktType?.convertToClass()
            superClassName = superClass?.name
            val isAny = superClassName == StandardClassIds.Any.shortClassName.asString()
            paramList = renderParamList(superClass, isAny)
            returnTypeString = if (superClass == null || superClassName == null || isAny) "" else if (superClass!!.isInterface()) ": $superClassName" else ": $superClassName()"

            //if (qualifiedElement == refExpr) {
            //    // not qualified
            //    targetParents.addAll(element.parents.filterIsInstance<KtClassOrObject>().toList())
            //    targetParents += element.containingFile
            //}
            //else {
            //    val receiver =
            //        when (qualifiedElement) {
            //            is KtQualifiedExpression -> qualifiedElement.receiverExpression.resolveExpression()
            //            is KtUserType -> null
            //            else -> null
            //        }
            //
            //    receiver?.psi?.let { targetParents += it }
            //}

            //if (targetParents.isEmpty()) {
            //    targetParents += element.containingFile
            //}
            //if (getContainer(refExpr) == null) return null

            val (classKinds, targetParents) = getPossibleClassKindsAndParents(refExpr)
            return classKinds.map { kind ->
                val applicableParents = targetParents
                    .filter {
                        if (kind == ClassKind.OBJECT && it is KtClass) {
                            if (it.isInner() || it.isLocal) false
                            else if (it.isEnum() && it == superClass) false // can't create `object` in enum of enum type
                            else true
                        }
                        else true
                    }
                CreateKotlinClassAction(
                    refExpr,
                    kind,
                    applicableParents,
                    false,
                    refExpr.getReferencedName(),
                    superClassName,
                    paramList!!,
                    returnTypeString
                )
            }
        }
    }

    private fun renderParamList(ktClass: KtClass?, isAny: Boolean): String {
        if (ktClass == null || isAny) return ""
        val prefix = if (ktClass.isAnnotation()) "val " else ""
        val primaryConstructor = ktClass.primaryConstructor
        val parameters = primaryConstructor?.valueParameterList?.parameters ?: listOf()
        val renderedParameters = parameters.indices.joinToString(", ") { i -> "${prefix}p$i: Any" }
        return if (parameters.isNotEmpty() || primaryConstructor != null)
            "($renderedParameters)"
        else
            renderedParameters
    }

    context (KaSession)
    private fun getPossibleClassKindsAndParents(element: KtSimpleNameExpression): Pair<List<ClassKind>,List<PsiElement>> {
        fun isEnum(element: PsiElement): Boolean {
            return when (element) {
                is KtClass -> element.isEnum()
                is PsiClass -> element.isEnum
                else -> false
            }
        }

        val name = element.getReferencedName()

        val fullCallExpr = getFullCallExpression(element) ?: return Pair(emptyList(), emptyList())
        if (fullCallExpr.getAssignmentByLHS() != null) return Pair(emptyList(), emptyList())

        val inImport = element.getNonStrictParentOfType<KtImportDirective>() != null
        if (inImport || isQualifierExpected(element)) {
            val receiverSelector =
                (fullCallExpr as? KtQualifiedExpression)?.receiverExpression?.getQualifiedElementSelector() as? KtReferenceExpression
            val qualifierDescriptor = receiverSelector?.resolveExpression()

            val targetParents = getTargetParentsByQualifier(element, receiverSelector != null, qualifierDescriptor)
                .ifEmpty { return Pair(emptyList(), emptyList()) }

            //targetParents.forEach {
            //    if (element.getCreatePackageFixIfApplicable(it) != null) return emptyList()
            //}

            if (!name.checkClassName()) return Pair(emptyList(), emptyList())

            val classKinds = ClassKind.entries.filter {
                when (it) {
                    ClassKind.ANNOTATION_CLASS -> inImport
                    ClassKind.ENUM_ENTRY -> inImport && targetParents.any { parent -> isEnum(parent) }
                    else -> true
                }
            }
            return Pair(classKinds, targetParents)
        }

        val targetParents =
        when (val receiver = (fullCallExpr as? KtQualifiedExpression)?.receiverExpression?.resolveExpression()) {
            null -> getTargetParentsByQualifier(fullCallExpr, false, null)
            else -> getTargetParentsByQualifier(fullCallExpr, true, receiver)
        }

        if (targetParents.isEmpty()) return Pair(emptyList(), emptyList())
        val parent = element.parent
        if (parent is KtClassLiteralExpression && parent.receiverExpression == element) {
            return Pair(listOf(ClassKind.PLAIN_CLASS, ClassKind.ENUM_CLASS, ClassKind.INTERFACE, ClassKind.ANNOTATION_CLASS, ClassKind.OBJECT), targetParents)
        }

        val allKinds = listOf(ClassKind.OBJECT, ClassKind.ENUM_ENTRY)

        analyze (element) {
            val expectedType: KaType? = fullCallExpr.expectedType

            val classKinds = allKinds.filter { classKind ->
                targetParents.any { targetParent ->
                    (expectedType == null || getClassKindFilter(expectedType, targetParent)(classKind)) && when (classKind) {
                        ClassKind.OBJECT -> expectedType == null || !isEnum(targetParent)
                        ClassKind.ENUM_ENTRY -> isEnum(targetParent)
                        else -> false
                    }
                }
            }
            return Pair(classKinds,targetParents)
        }
    }

    private fun getTargetParentsByQualifier(
        element: KtElement,
        isQualified: Boolean,
        qualifierDescriptor: KtSymbol?
    ): List<PsiElement> {
        val file = element.containingKtFile
        val project = file.project
        val targetParents: List<PsiElement> = when {
            !isQualified ->
                element.parents.filterIsInstance<KtClassOrObject>().toList() + file
            qualifierDescriptor is KaClassOrObjectSymbol ->
                listOfNotNull(qualifierDescriptor.psi)
            qualifierDescriptor is KaPackageSymbol ->
                if (qualifierDescriptor.fqName != file.packageFqName) {
                    listOfNotNull(JavaPsiFacade.getInstance(project).findPackage(qualifierDescriptor.fqName.asString()))
                } else listOf(file)
            else ->
                emptyList()
        }
        return targetParents.filter { it.canRefactorElement() }
    }

    context (KaSession)
    private fun getClassKindFilter(expectedType: KtType, containingDeclaration: PsiElement): (ClassKind) -> Boolean {
        if (expectedType.isAny) {
            return { _ -> true }
        }

        val canHaveSubtypes = isInheritable(expectedType) || !(expectedType.containsStarProjections()) || expectedType.isUnit
        val isEnum = expectedType is KaNonErrorClassType && expectedType.isEnum()

        if (!(canHaveSubtypes || isEnum) || expectedType is KaTypeParameterType) return { _ -> false }

        return { classKind ->
            when (classKind) {
                ClassKind.ENUM_ENTRY -> isEnum && containingDeclaration == expectedType.convertToClass()
                ClassKind.INTERFACE -> containingDeclaration !is PsiClass
                        || (expectedType is KtNonErrorClassType && expectedType.isInterface())
                else -> canHaveSubtypes
            }
        }
    }

    context(KaSession)
    private fun KtType.isInterface(): Boolean {
        if (this !is KtNonErrorClassType) return false
        val classSymbol = classSymbol
        return classSymbol is KaClassOrObjectSymbol && classSymbol.classKind == KaClassKind.INTERFACE
    }

    context(KaSession)
    private fun isInheritable(type: KtType): Boolean {
        return type.convertToClass()?.isInheritable() == true
    }

    private fun KtType.containsStarProjections(): Boolean = this is KaNonErrorClassType && ownTypeArguments.any { it is org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection || it.type?.containsStarProjections() == true}
}