// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.predicates

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex
import com.intellij.psi.impl.java.stubs.index.JavaShortClassNameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.elementType
import com.intellij.structuralsearch.StructuralSearchUtil
import com.intellij.structuralsearch.impl.matcher.MatchContext
import com.intellij.structuralsearch.impl.matcher.predicates.MatchPredicate
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate
import org.jetbrains.kotlin.builtins.getReceiverTypeFromFunctionType
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.debugger.sequence.psi.resolveType
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.idea.refactoring.fqName.getKotlinFqName
import org.jetbrains.kotlin.idea.search.allScope
import org.jetbrains.kotlin.idea.structuralsearch.resolveKotlinType
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.isNull
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeProjection
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable
import org.jetbrains.kotlin.types.typeUtil.supertypes

class KotlinExprTypePredicate(
    private val search: String,
    private val withinHierarchy: Boolean,
    private val ignoreCase: Boolean,
    private val target: Boolean,
    private val baseName: String,
    private val regex: Boolean
) : MatchPredicate() {

    override fun match(matchedNode: PsiElement, start: Int, end: Int, context: MatchContext): Boolean {
        val searchedTypeNames = if (regex) listOf() else search.split('|')
        if (matchedNode is KtExpression && matchedNode.isNull() && searchedTypeNames.contains("null")) return true
        val node = StructuralSearchUtil.getParentIfIdentifier(matchedNode)
        val type = when {
            node is KtDeclaration -> node.resolveKotlinType()
            node is KtExpression -> try {
                node.resolveType()
            } catch (e: Exception) {
                if (e is ControlFlowException) throw e
                null
            }
            node is KtStringTemplateEntry && node !is KtSimpleNameStringTemplateEntry -> null
            node is KtSimpleNameStringTemplateEntry -> node.expression?.resolveType()
            else -> throw IllegalStateException(KotlinBundle.message("error.type.filter.node"))
        } ?: return false

        val project = node.project
        val scope = project.allScope()

        if (regex) {
            val delegate = RegExpPredicate(search, !ignoreCase, baseName, false, target)
            val typesToTest = mutableListOf(type)
            if (withinHierarchy) typesToTest.addAll(type.supertypes())

            return typesToTest.any {
                delegate.doMatch(DescriptorRenderer.SHORT_NAMES_IN_TYPES.renderType(it), context, matchedNode)
                        || delegate.doMatch(DescriptorRenderer.FQ_NAMES_IN_TYPES.renderType(it), context, matchedNode)
            }
        }

        val factory = KtPsiFactory(project, false)
        return searchedTypeNames
            .filter { it != "null" }
            .map(factory::createType)
            .any { typeReference ->
                matchTypeReference(type, typeReference, project, scope)
                        || withinHierarchy
                        && type.supertypes().any { superType -> matchTypeReference(superType, typeReference, project, scope) }
            }
    }

    companion object {
        private fun matchTypeReference(
            type: KotlinType?, typeReference: KtTypeReference?, project: Project, scope: GlobalSearchScope
        ): Boolean {
            if (type == null || typeReference == null) return type == null && typeReference == null
            val element = typeReference.typeElement
            return matchTypeElement(type, element, project, scope)
        }

        private fun matchTypeElement(
            type: KotlinType, typeElement: KtTypeElement?, project: Project, scope: GlobalSearchScope
        ): Boolean {
            if (typeElement == null) return false
            return when (typeElement) {
                is KtFunctionType -> !type.isMarkedNullable && matchFunctionType(type, typeElement, project, scope)
                is KtUserType -> !type.isMarkedNullable && matchUserType(type, typeElement, project, scope)
                is KtNullableType -> type.isMarkedNullable && matchTypeElement(
                    type.makeNotNullable(),
                    typeElement.innerType,
                    project,
                    scope
                )
                else -> return false
            }
        }

        private fun matchFunctionType(
            type: KotlinType, functionType: KtFunctionType, project: Project, scope: GlobalSearchScope
        ): Boolean {
            val matchArguments = functionType.typeArgumentsAsTypes.isEmpty() ||
                    type.arguments.size == functionType.typeArgumentsAsTypes.size
                    && type.arguments.zip(functionType.typeArgumentsAsTypes).all { (projection, reference) ->
                matchProjection(projection, reference) && matchTypeReference(
                    projection.type,
                    reference,
                    project,
                    scope
                )
            }

            return matchArguments && matchFunctionName(type, functionType) && matchTypeReference(
                type.getReceiverTypeFromFunctionType(),
                functionType.receiverTypeReference,
                project,
                scope
            )
        }

        private fun matchUserType(type: KotlinType, userType: KtUserType, project: Project, scope: GlobalSearchScope): Boolean {
            var className = userType.referencedName ?: return false

            var qualifier = userType.qualifier
            while (qualifier != null) {
                className = "${qualifier.referencedName}.$className"
                qualifier = qualifier.qualifier
            }

            val matchArguments = userType.typeArguments.isEmpty() ||
                    type.arguments.size == userType.typeArguments.size
                    && type.arguments.zip(userType.typeArguments).all { (projection, reference) ->
                compareProjectionKind(projection, reference.projectionKind) && (projection.isStarProjection || matchTypeReference(
                    projection.type,
                    reference.typeReference,
                    project,
                    scope
                ))
            }

            return matchArguments && matchString(type, className, project, scope)
        }

        private fun matchFunctionName(type: KotlinType, typeElement: KtTypeElement): Boolean {
            val parent = typeElement.parent
            val typeArguments = typeElement.typeArgumentsAsTypes
            return when {
                parent is KtTypeReference &&
                        parent.modifierList?.allChildren?.any { it.elementType == KtTokens.SUSPEND_KEYWORD } == true
                -> "${type.fqName}" == "kotlin.coroutines.SuspendFunction${typeArguments.size - 1}"
                else -> "${type.fqName}" == "kotlin.Function${typeArguments.size - 1}"
                        || typeArguments.size == 1 && "${type.fqName}" == "kotlin.Function"
            }
        }

        private fun matchString(type: KotlinType, className: String, project: Project, scope: GlobalSearchScope): Boolean {
            val fq = className.contains(".")

            // Kotlin indexes
            when {
                fq -> if (KotlinFullClassNameIndex.getInstance()[className, project, scope].any {
                        it.getKotlinFqName() == type.fqName
                    }) return true
                else -> if (KotlinClassShortNameIndex.getInstance()[className, project, scope].any {
                        it.getKotlinFqName() == type.fqName
                    }) return true
            }

            // Java indexes
            when {
                fq -> if (JavaFullClassNameIndex.getInstance()[className.hashCode(), project, scope].any {
                        it.getKotlinFqName() == type.fqName
                    }) return true
                else -> if (JavaShortClassNameIndex.getInstance()[className, project, scope].any {
                        it.getKotlinFqName() == type.fqName
                    }) return true
            }

            return false
        }

        private fun matchProjection(projection: TypeProjection, typeReference: KtTypeReference?): Boolean {
            val parent = typeReference?.parent
            if (parent !is KtTypeProjection) return projection.projectionKind == Variance.INVARIANT
            return compareProjectionKind(projection, parent.projectionKind)
        }

        private fun compareProjectionKind(projection: TypeProjection, projectionKind: KtProjectionKind) = when (projectionKind) {
            KtProjectionKind.IN -> projection.projectionKind == Variance.IN_VARIANCE
            KtProjectionKind.OUT -> projection.projectionKind == Variance.OUT_VARIANCE
            KtProjectionKind.STAR -> projection.isStarProjection
            KtProjectionKind.NONE -> projection.projectionKind == Variance.INVARIANT
        }
    }

}