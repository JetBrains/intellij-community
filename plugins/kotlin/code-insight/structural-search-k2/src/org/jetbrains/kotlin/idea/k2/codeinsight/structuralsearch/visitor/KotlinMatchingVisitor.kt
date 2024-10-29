// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.visitor

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.elementType
import com.intellij.structuralsearch.MatchUtil
import com.intellij.structuralsearch.impl.matcher.CompiledPattern
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor
import com.intellij.structuralsearch.impl.matcher.handlers.LiteralWithSubstitutionHandler
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.*
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.predicates.KotlinAlsoMatchCompanionObjectPredicate
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.predicates.KotlinAlsoMatchValVarPredicate
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.predicates.KotlinExprTypePredicate
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.predicates.KotlinMatchCallSemantics
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.declarationsSearch.HierarchySearchRequest
import org.jetbrains.kotlin.idea.search.declarationsSearch.searchInheritors
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.impl.KDocImpl
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.util.match

class KotlinMatchingVisitor(private val myMatchingVisitor: GlobalMatchingVisitor) : SSRKtVisitor() {
    /** Gets the next element in the query tree and removes unnecessary parentheses. */
    private inline fun <reified T> getTreeElementDepar(): T? = when (val element = myMatchingVisitor.element) {
        is KtParenthesizedExpression -> {
            val deparenthesized = KtPsiUtil.deparenthesize(element)
            if (deparenthesized is T) deparenthesized else {
                myMatchingVisitor.result = false
                null
            }
        }
        else -> getTreeElement<T>()
    }

    /** Gets the next element in the tree */
    private inline fun <reified T> getTreeElement(): T? = when (val element = myMatchingVisitor.element) {
        is T -> element
        else -> {
            myMatchingVisitor.result = false
            null
        }
    }

    private inline fun <reified T:KtElement> factory(context: PsiElement, f: KtPsiFactory.() -> T): T {
        val psiFactory = KtPsiFactory(context.project, true)
        val result = psiFactory.f()
        (result.containingFile as KtFile).analysisContext = context
        return result
    }

    private fun GlobalMatchingVisitor.matchSequentially(elements: List<PsiElement?>, elements2: List<PsiElement?>) =
        matchSequentially(elements.toTypedArray(), elements2.toTypedArray())

    private fun GlobalMatchingVisitor.matchInAnyOrder(elements: List<PsiElement?>, elements2: List<PsiElement?>) =
        matchInAnyOrder(elements.toTypedArray(), elements2.toTypedArray())

    private fun GlobalMatchingVisitor.matchNormalized(
        element: KtExpression?,
        element2: KtExpression?,
        returnExpr: Boolean = false
    ): Boolean {
        val (e1, e2) =
            if (element is KtBlockExpression && element2 is KtBlockExpression) element to element2
            else normalizeExpressions(element, element2, returnExpr)

        val impossible = e1?.let {
            val handler = getHandler(it)
            e2 !is KtBlockExpression && handler is SubstitutionHandler && handler.minOccurs > 1
        } ?: false

        return !impossible && match(e1, e2)
    }

    private fun getHandler(element: PsiElement) = myMatchingVisitor.matchContext.pattern.getHandler(element)

    private fun matchTextOrVariable(el1: PsiElement?, el2: PsiElement?): Boolean {
        if (el1 == null) return true
        if (el2 == null) return false
        return substituteOrMatchText(el1, el2)
    }

    private fun matchTextOrVariableEq(el1: PsiElement?, el2: PsiElement?): Boolean {
        if (el1 == null && el2 == null) return true
        if (el1 == null) return false
        if (el2 == null) return false
        return substituteOrMatchText(el1, el2)
    }

    private fun substituteOrMatchText(el1: PsiElement, el2: PsiElement): Boolean {
        return when (val handler = getHandler(el1)) {
            is SubstitutionHandler -> handler.validate(el2, myMatchingVisitor.matchContext)
            else -> myMatchingVisitor.matchText(el1, el2)
        }
    }

    override fun visitLeafPsiElement(leafPsiElement: LeafPsiElement) {
        val other = getTreeElementDepar<LeafPsiElement>() ?: return

        // Match element type
        if (!myMatchingVisitor.setResult(leafPsiElement.elementType == other.elementType)) return

        when (leafPsiElement.elementType) {
            KDocTokens.TEXT -> {
                myMatchingVisitor.result = when (val handler = leafPsiElement.getUserData(CompiledPattern.HANDLER_KEY)) {
                    is LiteralWithSubstitutionHandler -> handler.match(leafPsiElement, other, myMatchingVisitor.matchContext)
                    else -> substituteOrMatchText(leafPsiElement, other)
                }
            }
            KDocTokens.TAG_NAME, KtTokens.IDENTIFIER -> myMatchingVisitor.result = substituteOrMatchText(leafPsiElement, other)
        }
    }

    override fun visitArrayAccessExpression(expression: KtArrayAccessExpression) {
        val other = getTreeElementDepar<KtExpression>() ?: return
        myMatchingVisitor.result = when (other) {
            is KtArrayAccessExpression -> myMatchingVisitor.match(expression.arrayExpression, other.arrayExpression)
                    && myMatchingVisitor.matchSons(expression.indicesNode, other.indicesNode)
            is KtDotQualifiedExpression -> myMatchingVisitor.match(expression.arrayExpression, other.receiverExpression)
                    && other.calleeName == "${OperatorNameConventions.GET}"
                    && myMatchingVisitor.matchSequentially(
                expression.indexExpressions,
                (other.selectorExpression as KtCallExpression).valueArguments.map(KtValueArgument::getArgumentExpression)
            )
            else -> false
        }

    }

    override fun visitBinaryExpression(expression: KtBinaryExpression) {
        val other = getTreeElementDepar<KtBinaryExpression>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(expression.left, other.left)
                && myMatchingVisitor.match(expression.operationReference, other.operationReference)
                && myMatchingVisitor.match(expression.right, other.right)
    }

    override fun visitBlockExpression(expression: KtBlockExpression) {
        val other = getTreeElementDepar<KtBlockExpression>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchSequentially(expression.statements, other.statements)
    }

    override fun visitUnaryExpression(expression: KtUnaryExpression) {
        val other = getTreeElementDepar<KtExpression>() ?: return
        myMatchingVisitor.result = when (other) {
            is KtDotQualifiedExpression -> {
                myMatchingVisitor.match(expression.baseExpression, other.receiverExpression)
                        && OperatorConventions.UNARY_OPERATION_NAMES[expression.operationToken].toString() == other.calleeName
            }
            is KtUnaryExpression -> myMatchingVisitor.match(expression.baseExpression, other.baseExpression)
                    && myMatchingVisitor.match(expression.operationReference, other.operationReference)
            else -> false
        }
    }

    override fun visitParenthesizedExpression(expression: KtParenthesizedExpression) {
        fun KtExpression.countParenthesize(initial: Int = 0): Int {
            val parentheses = children.firstOrNull { it is KtParenthesizedExpression } as KtExpression?
            return parentheses?.countParenthesize(initial + 1) ?: initial
        }

        val other = getTreeElement<KtParenthesizedExpression>() ?: return
        if (!myMatchingVisitor.setResult(expression.countParenthesize() == other.countParenthesize())) return
        myMatchingVisitor.result = myMatchingVisitor.match(KtPsiUtil.deparenthesize(expression), KtPsiUtil.deparenthesize(other))
    }

    override fun visitConstantExpression(expression: KtConstantExpression) {
        val other = getTreeElementDepar<KtExpression>() ?: return
        myMatchingVisitor.result = substituteOrMatchText(expression, other)
    }

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        val other = getTreeElementDepar<PsiElement>() ?: return
        val exprHandler = getHandler(expression)
        if (other is KtReferenceExpression && exprHandler is SubstitutionHandler) {
            analyze(other) {
                val fqName = other.mainReference.resolveToSymbol()?.psi?.kotlinFqName
                val predicate = exprHandler.findPredicate(RegExpPredicate::class.java)
                if (predicate != null && fqName != null && predicate.doMatch(fqName.toString(), myMatchingVisitor.matchContext, other)) {
                    myMatchingVisitor.result = true
                    exprHandler.addResult(other, myMatchingVisitor.matchContext)
                    return
                }
            }
        }

        // Match Int::class with X.Int::class
        val skipReceiver = other.parent is KtDoubleColonExpression
                && other is KtDotQualifiedExpression
                && myMatchingVisitor.match(expression, other.selectorExpression)

        myMatchingVisitor.result = skipReceiver || substituteOrMatchText(
            expression.getReferencedNameElement(),
            if (other is KtSimpleNameExpression) other.getReferencedNameElement() else other
        )

        val handler = getHandler(expression.getReferencedNameElement())
        if (myMatchingVisitor.result && handler is SubstitutionHandler) {
            myMatchingVisitor.result = handler.handle(
                if (other is KtSimpleNameExpression) other.getReferencedNameElement() else other,
                myMatchingVisitor.matchContext
            )
        }
    }

    override fun visitContinueExpression(expression: KtContinueExpression) {
        val other = getTreeElementDepar<KtContinueExpression>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(expression.getTargetLabel(), other.getTargetLabel())
    }

    override fun visitBreakExpression(expression: KtBreakExpression) {
        val other = getTreeElementDepar<KtBreakExpression>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(expression.getTargetLabel(), other.getTargetLabel())
    }

    override fun visitThisExpression(expression: KtThisExpression) {
        val other = getTreeElementDepar<KtThisExpression>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(expression.getTargetLabel(), other.getTargetLabel())
    }

    override fun visitSuperExpression(expression: KtSuperExpression) {
        val other = getTreeElementDepar<KtSuperExpression>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(expression.getTargetLabel(), other.getTargetLabel())
                && myMatchingVisitor.match(expression.superTypeQualifier, other.superTypeQualifier)
    }

    override fun visitReturnExpression(expression: KtReturnExpression) {
        val other = getTreeElementDepar<KtReturnExpression>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(expression.getTargetLabel(), other.getTargetLabel())
                && myMatchingVisitor.match(expression.returnedExpression, other.returnedExpression)
    }

    override fun visitFunctionType(type: KtFunctionType) {
        val other = getTreeElementDepar<KtFunctionType>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(type.receiverTypeReference, other.receiverTypeReference)
                && myMatchingVisitor.match(type.parameterList, other.parameterList)
                && myMatchingVisitor.match(type.returnTypeReference, other.returnTypeReference)
    }

    override fun visitUserType(type: KtUserType) {
        val other = myMatchingVisitor.element

        myMatchingVisitor.result = when (other) {
            is KtUserType -> {
                type.qualifier?.let { typeQualifier -> // if query has fq type
                    myMatchingVisitor.match(typeQualifier, other.qualifier) // recursively match qualifiers
                            && myMatchingVisitor.match(type.referenceExpression, other.referenceExpression)
                            && myMatchingVisitor.match(type.typeArgumentList, other.typeArgumentList)
                } ?: let { // no fq type
                    myMatchingVisitor.match(type.referenceExpression, other.referenceExpression)
                            && myMatchingVisitor.match(type.typeArgumentList, other.typeArgumentList)
                }
            }
            is KtTypeElement -> matchTextOrVariable(type.referenceExpression, other)
            else -> false
        }
    }

    override fun visitNullableType(nullableType: KtNullableType) {
        val other = getTreeElementDepar<KtNullableType>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(nullableType.innerType, other.innerType)
    }

    override fun visitDynamicType(type: KtDynamicType) {
        myMatchingVisitor.result = myMatchingVisitor.element is KtDynamicType
    }

    override fun visitTypeReference(typeReference: KtTypeReference) {
        val other = getTreeElementDepar<KtTypeReference>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchSons(typeReference, other)
        val handler = getHandler(typeReference)
        if (myMatchingVisitor.result && handler is SubstitutionHandler) {
            handler.reset()
        }
    }

    override fun visitQualifiedExpression(expression: KtQualifiedExpression) {
        val other = getTreeElementDepar<KtQualifiedExpression>() ?: return
        myMatchingVisitor.result = expression.operationSign == other.operationSign
                && myMatchingVisitor.match(expression.selectorExpression, other.selectorExpression)
                && myMatchingVisitor.match(expression.receiverExpression, other.receiverExpression)
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        val other = getTreeElementDepar<KtExpression>() ?: return
        val receiverHandler = getHandler(expression.receiverExpression)
        if (receiverHandler is SubstitutionHandler && receiverHandler.minOccurs == 0 && other !is KtDotQualifiedExpression) { // can match without receiver
            myMatchingVisitor.result = other.parent !is KtDotQualifiedExpression
                    && other.parent !is KtCallExpression // don't match name reference of calls
                    && myMatchingVisitor.match(expression.selectorExpression, other)
                    && receiverHandler.findPredicate(KotlinExprTypePredicate::class.java)?.let { predicate ->
                        analyze(other) {
                            other.findDispatchReceiver()?.let {
                                type -> predicate.match(type)
                            } ?: false
                        }
                    } ?: true
        } else {
            myMatchingVisitor.result = other is KtDotQualifiedExpression
                    && myMatchingVisitor.match(expression.selectorExpression, other.selectorExpression)
                    && myMatchingVisitor.matchOptionally(expression.receiverExpression, other.receiverExpression)
        }
    }

    override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
        val other = getTreeElementDepar<KtLambdaExpression>() ?: return
        val lambdaVP = lambdaExpression.valueParameters
        val otherVP = other.valueParameters

        myMatchingVisitor.result = (!lambdaExpression.functionLiteral.hasParameterSpecification()
                || myMatchingVisitor.matchSequentially(lambdaVP, otherVP)
                || lambdaVP.sumOf { p -> getHandler(p).let { if (it is SubstitutionHandler) it.minOccurs else 1 } } == 1
                && !other.functionLiteral.hasParameterSpecification()
                && other.functionLiteral.valueParameters.size == 1)
                && myMatchingVisitor.match(lambdaExpression.bodyExpression, other.bodyExpression)
    }

    override fun visitTypeProjection(typeProjection: KtTypeProjection) {
        val other = getTreeElementDepar<KtTypeProjection>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(typeProjection.typeReference, other.typeReference)
                && myMatchingVisitor.match(typeProjection.modifierList, other.modifierList)
                && typeProjection.projectionKind == other.projectionKind
    }

    override fun visitTypeArgumentList(typeArgumentList: KtTypeArgumentList) {
        val other = getTreeElementDepar<KtTypeArgumentList>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchSequentially(typeArgumentList.arguments, other.arguments)
    }

    override fun visitArgument(argument: KtValueArgument) {
        val other = getTreeElementDepar<KtValueArgument>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(argument.getArgumentExpression(), other.getArgumentExpression())
                && (!argument.isNamed() || !other.isNamed() || matchTextOrVariable(
            argument.getArgumentName(), other.getArgumentName()
        ))
    }

    private fun MutableList<KtValueArgument>.addDefaultArguments(parameters: List<KtParameter?>) {
        if (parameters.isEmpty()) return
        val params = parameters.toTypedArray()
        var i = 0
        while (i < size) {
            val arg = get(i)
            if (arg.isNamed()) {
                params[parameters.indexOfFirst { it?.nameAsName == arg.getArgumentName()?.asName }] = null
                i++
            } else {
                val curParam = params[i] ?: throw IllegalStateException(
                    KotlinBundle.message("error.param.can.t.be.null.at.index.0.in.1", i, params.map { it?.text })
                )
                params[i] = null
                if (curParam.isVarArg) {
                    analyze(arg) {
                        val varArgType = arg.getArgumentExpression()?.expressionType
                        var curArg: KtValueArgument? = arg
                        while (varArgType != null && varArgType == curArg?.getArgumentExpression()?.expressionType || curArg?.isSpread == true) {
                            i++
                            curArg = getOrNull(i)

                        }
                    }
                } else i++
            }
        }
        params.filterNotNull().forEach {
            add(factory(myMatchingVisitor.element) {
                createArgument(it.defaultValue, it.nameAsName, reformat = false)
            })
        }
    }

    private fun KtCallElement.resolveParameters(): List<KtParameter> {
        return analyze(this) {
            val callInfo = resolveToCall()?.successfulFunctionCallOrNull() ?: return emptyList()
            return callInfo.partiallyAppliedSymbol.signature.valueParameters.mapNotNull { it.symbol.psi as? KtParameter? }
        }
    }

    private fun KtCallElement.matchArguments(other: KtCallElement): Boolean {
        val handler = getHandler(calleeExpression ?: return false)
        return if (handler is SubstitutionHandler && handler.findPredicate(KotlinMatchCallSemantics::class.java) != null) {
            matchArgumentsSemantically(other)
        } else {
            myMatchingVisitor.matchSequentially(valueArgumentList?.arguments ?: emptyList(), other.valueArgumentList?.arguments ?: emptyList())
                    && myMatchingVisitor.matchSequentially(lambdaArguments, other.lambdaArguments)
        }
    }

    private fun KtCallElement.matchArgumentsSemantically(other: KtCallElement): Boolean {
        val parameters = other.resolveParameters().distinct()
        val valueArgList = valueArgumentList
        val otherValueArgList = other.valueArgumentList
        val lambdaArgList = lambdaArguments
        val otherLambdaArgList = other.lambdaArguments
        if (valueArgList != null) {
            val handler = getHandler(valueArgList)
            val normalizedOtherArgs = (otherValueArgList?.arguments?.toMutableList() ?: mutableListOf()).apply {
                addAll(otherLambdaArgList)
                addDefaultArguments(parameters)
            }
            if (normalizedOtherArgs.isEmpty() && handler is SubstitutionHandler && handler.minOccurs == 0) {
                return myMatchingVisitor.matchSequentially(lambdaArgList, otherLambdaArgList)
            }
            val normalizedArgs = valueArgList.arguments.toMutableList()
            normalizedArgs.addAll(lambdaArgList)
            return matchArgumentsSemantically(normalizedArgs, normalizedOtherArgs)
        }
        return matchArgumentsSemantically(lambdaArgList, otherLambdaArgList)
    }

    private fun matchArgumentsSemantically(queryArgs: List<KtValueArgument>, codeArgs: List<KtValueArgument>): Boolean {
        var queryIndex = 0
        var codeIndex = 0
        while (queryIndex < queryArgs.size) {
            val queryArg = queryArgs[queryIndex]
            val codeArg = codeArgs.getOrElse(codeIndex) { return@matchArgumentsSemantically false }
            if (getHandler(queryArg) is SubstitutionHandler) {
                return myMatchingVisitor.matchSequentially(
                    queryArgs.subList(queryIndex, queryArgs.lastIndex + 1),
                    codeArgs.subList(codeIndex, codeArgs.lastIndex + 1)
                )
            }

            // varargs declared in call matching with one-to-one argument passing
            if (queryArg.isSpread && !codeArg.isSpread) {
                val spreadArgExpr = queryArg.getArgumentExpression()
                if (spreadArgExpr is KtCallExpression) {
                    spreadArgExpr.valueArguments.forEach { spreadedArg ->
                        if (!myMatchingVisitor.match(spreadedArg, codeArgs[codeIndex++])) return@matchArgumentsSemantically false
                    }
                    queryIndex++
                    continue
                }   // can't match array that is not created in the call itself
                myMatchingVisitor.result = false
                return myMatchingVisitor.result
            }
            if (!queryArg.isSpread && codeArg.isSpread) {
                val spreadArgExpr = codeArg.getArgumentExpression()
                if (spreadArgExpr is KtCallExpression) {
                    spreadArgExpr.valueArguments.forEach { spreadedArg ->
                        if (!myMatchingVisitor.match(queryArgs[queryIndex++], spreadedArg)) return@matchArgumentsSemantically false
                    }
                    codeIndex++
                    continue
                }
                return false// can't match array that is not created in the call itself
            }
            // normal argument matching
            if (!myMatchingVisitor.match(queryArg, codeArg)) {
                return if (queryArg.isNamed() || codeArg.isNamed()) { // start comparing for out of order arguments
                    myMatchingVisitor.matchInAnyOrder(
                        queryArgs.subList(queryIndex, queryArgs.lastIndex + 1),
                        codeArgs.subList(codeIndex, codeArgs.lastIndex + 1)
                    )
                } else false
            }
            queryIndex++
            codeIndex++
        }
        return codeIndex == codeArgs.size
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        val other = getTreeElementDepar<KtExpression>() ?: return
        myMatchingVisitor.result = when (other) {
            is KtCallExpression -> {
                myMatchingVisitor.match(expression.calleeExpression, other.calleeExpression)
                        && myMatchingVisitor.match(expression.typeArgumentList, other.typeArgumentList)
                        && expression.matchArguments(other)
            }
            is KtDotQualifiedExpression -> other.selectorExpression is KtCallExpression
                    && myMatchingVisitor.match(expression.calleeExpression, other.receiverExpression)
                    && other.calleeName == "${OperatorNameConventions.INVOKE}"
                    && expression.matchArguments(other.selectorExpression as KtCallElement)
            else -> false
        }
    }

    override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression) {
        val other = getTreeElementDepar<KtCallableReferenceExpression>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(expression.callableReference, other.callableReference)
                && myMatchingVisitor.matchOptionally(expression.receiverExpression, other.receiverExpression)
    }

    override fun visitTypeParameter(parameter: KtTypeParameter) {
        val other = getTreeElementDepar<KtTypeParameter>() ?: return
        myMatchingVisitor.result = substituteOrMatchText(parameter.firstChild, other.firstChild) // match generic identifier
                && myMatchingVisitor.match(parameter.extendsBound, other.extendsBound)
                && parameter.variance == other.variance
        parameter.nameIdentifier?.let { nameIdentifier ->
            val handler = getHandler(nameIdentifier)
            if (myMatchingVisitor.result && handler is SubstitutionHandler) {
                handler.handle(other.nameIdentifier, myMatchingVisitor.matchContext)
            }
        }
    }

    override fun visitParameter(parameter: KtParameter) {
        val other = getTreeElementDepar<KtParameter>() ?: return
        val otherNameIdentifier = if (getHandler(parameter) is SubstitutionHandler
            && parameter.nameIdentifier != null
            && other.nameIdentifier == null
        ) other else other.nameIdentifier
        myMatchingVisitor.matchContext.pushResult()
        myMatchingVisitor.result = myMatchingVisitor.match(parameter.typeReference, other.typeReference)
                && myMatchingVisitor.match(parameter.defaultValue, other.defaultValue)
                && (parameter.isVarArg == other.isVarArg || getHandler(parameter) is SubstitutionHandler)
                && myMatchingVisitor.match(parameter.valOrVarKeyword, other.valOrVarKeyword)
                && (parameter.nameIdentifier == null || matchTextOrVariable(parameter.nameIdentifier, otherNameIdentifier))
                && myMatchingVisitor.match(parameter.modifierList, other.modifierList)
                && myMatchingVisitor.match(parameter.destructuringDeclaration, other.destructuringDeclaration)
        parameter.nameIdentifier?.let { nameIdentifier ->
            val handler = getHandler(nameIdentifier)
            if (myMatchingVisitor.result && handler is SubstitutionHandler) {
                myMatchingVisitor.scopeMatch(parameter.nameIdentifier,
                                             myMatchingVisitor.matchContext.pattern.isTypedVar(parameter.nameIdentifier), otherNameIdentifier)
            }
        }
    }

    override fun visitTypeParameterList(list: KtTypeParameterList) {
        val other = getTreeElementDepar<KtTypeParameterList>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchSequentially(list.parameters, other.parameters)
    }

    override fun visitParameterList(list: KtParameterList) {
        val other = getTreeElementDepar<KtParameterList>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchSequentially(list.parameters, other.parameters)
    }

    override fun visitConstructorDelegationCall(call: KtConstructorDelegationCall) {
        val other = getTreeElementDepar<KtConstructorDelegationCall>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(call.calleeExpression, other.calleeExpression)
                && myMatchingVisitor.match(call.typeArgumentList, other.typeArgumentList)
                && call.matchArguments(other)
    }

    override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor) {
        val other = getTreeElementDepar<KtSecondaryConstructor>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(constructor.modifierList, other.modifierList)
                && myMatchingVisitor.match(constructor.typeParameterList, other.typeParameterList)
                && myMatchingVisitor.match(constructor.valueParameterList, other.valueParameterList)
                && myMatchingVisitor.match(constructor.getDelegationCallOrNull(), other.getDelegationCallOrNull())
                && myMatchingVisitor.match(constructor.bodyExpression, other.bodyExpression)
    }

    override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
        val other = getTreeElementDepar<KtPrimaryConstructor>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(constructor.modifierList, other.modifierList)
                && myMatchingVisitor.match(constructor.typeParameterList, other.typeParameterList)
                && myMatchingVisitor.match(constructor.valueParameterList, other.valueParameterList)
    }

    override fun visitAnonymousInitializer(initializer: KtAnonymousInitializer) {
        val other = getTreeElementDepar<KtAnonymousInitializer>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(initializer.body, other.body)
    }

    override fun visitClassBody(classBody: KtClassBody) {
        val other = getTreeElementDepar<KtClassBody>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchSonsInAnyOrder(classBody, other)
    }

    override fun visitSuperTypeCallEntry(call: KtSuperTypeCallEntry) {
        val other = getTreeElementDepar<KtSuperTypeCallEntry>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(call.calleeExpression, other.calleeExpression)
                && myMatchingVisitor.match(call.typeArgumentList, other.typeArgumentList)
                && call.matchArguments(other)
    }

    override fun visitSuperTypeEntry(specifier: KtSuperTypeEntry) {
        val other = getTreeElementDepar<KtSuperTypeEntry>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(specifier.typeReference, other.typeReference)
    }

    private fun matchTypeAgainstElement(
        type: String,
        element: PsiElement,
        other: PsiElement
    ): Boolean {
        return when (val predicate = (getHandler(element) as? SubstitutionHandler)?.findPredicate(RegExpPredicate::class.java)) {
            null -> element.text == type
                    // Ignore type parameters if absent from the pattern
                    || !element.text.contains('<') && element.text == type.removeTypeParameters()
            else -> predicate.doMatch(type, myMatchingVisitor.matchContext, other)
        }
    }

    override fun visitSuperTypeList(list: KtSuperTypeList) {
        val other = getTreeElementDepar<KtSuperTypeList>() ?: return

        val withinHierarchyEntries = list.entries.filter {
            val type = it.typeReference; type is KtTypeReference && getHandler(type).withinHierarchyTextFilterSet
        }
        (other.parent as? KtClassOrObject)?.let { klass ->
            analyze(klass) {
                val superTypes = klass.classSymbol?.superTypes ?: return
                withinHierarchyEntries.forEach { entry ->
                    val typeReference = entry.typeReference
                    if (!matchTextOrVariable(typeReference, klass.nameIdentifier) && typeReference != null && superTypes.none {
                            it.renderNames().any { type -> matchTypeAgainstElement(type, typeReference, other) }
                        }) {
                        myMatchingVisitor.result = false
                        return@visitSuperTypeList
                    }
                }
            }
        }
        myMatchingVisitor.result = myMatchingVisitor.matchInAnyOrder(list.entries.filter { it !in withinHierarchyEntries }, other.entries)
    }

    override fun visitClass(klass: KtClass) {
        val other = getTreeElementDepar<KtClass>() ?: return

        val identifier = klass.nameIdentifier
        val otherIdentifier = other.nameIdentifier
        var matchNameIdentifiers = matchTextOrVariable(identifier, otherIdentifier)
                || identifier != null && otherIdentifier != null
                && matchTypeAgainstElement(other.kotlinFqName.toString(), identifier, otherIdentifier)

        // Possible match if "within hierarchy" is set
        if (!matchNameIdentifiers && identifier != null && otherIdentifier != null) {
            val identifierHandler = getHandler(identifier)
            val checkHierarchyDown = identifierHandler.withinHierarchyTextFilterSet

            if (checkHierarchyDown) {
                // Check hierarchy down (down of pattern element = supertypes of code element)
                matchNameIdentifiers = analyze(other) {
                    other.classSymbol?.superTypes?.any { type ->
                        type.renderNames().any { renderedType ->
                            matchTypeAgainstElement(renderedType, identifier, otherIdentifier)
                        }
                    } ?: false
                }
            } else if (identifier.getUserData(KotlinCompilingVisitor.WITHIN_HIERARCHY) == true) {
                // Check hierarchy up (up of pattern element = inheritors of code element)
                matchNameIdentifiers = HierarchySearchRequest(
                    other,
                    GlobalSearchScope.allScope(other.project),
                    true
                ).searchInheritors().any { psiClass ->
                    arrayOf(psiClass.name, psiClass.qualifiedName).filterNotNull().any { renderedType ->
                        matchTypeAgainstElement(renderedType, identifier, otherIdentifier)
                    }
                }
            }
        }

        myMatchingVisitor.result = myMatchingVisitor.match(klass.getClassOrInterfaceKeyword(), other.getClassOrInterfaceKeyword())
                && myMatchingVisitor.match(klass.modifierList, other.modifierList)
                && matchNameIdentifiers
                && myMatchingVisitor.match(klass.typeParameterList, other.typeParameterList)
                && myMatchingVisitor.match(klass.primaryConstructor, other.primaryConstructor)
                && myMatchingVisitor.matchInAnyOrder(klass.secondaryConstructors, other.secondaryConstructors)
                && myMatchingVisitor.match(klass.getSuperTypeList(), other.getSuperTypeList())
                && myMatchingVisitor.match(klass.body, other.body)
                && myMatchingVisitor.match(klass.docComment, other.docComment)
        val handler = getHandler(klass.nameIdentifier!!)
        if (myMatchingVisitor.result && handler is SubstitutionHandler) {
            handler.handle(other.nameIdentifier, myMatchingVisitor.matchContext)
        }
    }

    override fun visitObjectLiteralExpression(expression: KtObjectLiteralExpression) {
        val other = getTreeElementDepar<KtObjectLiteralExpression>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(expression.objectDeclaration, other.objectDeclaration)
    }

    override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
        val other = getTreeElementDepar<KtObjectDeclaration>() ?: return
        val inferredNameIdentifier = declaration.nameIdentifier
            ?: declaration.takeIf(KtObjectDeclaration::isCompanion)
                ?.let { it.parents.match(KtClassBody::class, last = KtClass::class) ?: error("Can't typeMatch ${it.parent.parent}") }
                ?.nameIdentifier
        val handler = inferredNameIdentifier?.let { getHandler(inferredNameIdentifier) }
        val matchIdentifier = if (handler is SubstitutionHandler && handler.maxOccurs > 0 && handler.minOccurs == 0) {
            true // match count filter with companion object without identifier
        } else matchTextOrVariableEq(declaration.nameIdentifier, other.nameIdentifier)
        myMatchingVisitor.result =
            (declaration.isCompanion() == other.isCompanion() ||
                    (handler is SubstitutionHandler && handler.findPredicate(KotlinAlsoMatchCompanionObjectPredicate::class.java) != null))
                    && myMatchingVisitor.match(declaration.modifierList, other.modifierList)
                    && matchIdentifier
                    && myMatchingVisitor.match(declaration.getSuperTypeList(), other.getSuperTypeList())
                    && myMatchingVisitor.match(declaration.body, other.body)
        if (myMatchingVisitor.result && handler is SubstitutionHandler) {
            handler.handle(other.nameIdentifier, myMatchingVisitor.matchContext)
        }
    }

    private fun normalizeExpressionRet(expression: KtExpression?): KtExpression? = when {
        expression is KtBlockExpression && expression.statements.size == 1 -> expression.firstStatement?.let {
            if (it is KtReturnExpression) it.returnedExpression else it
        }
        else -> expression
    }

    private fun normalizeExpression(expression: KtExpression?): KtExpression? = when {
        expression is KtBlockExpression && expression.statements.size == 1 -> expression.firstStatement
        else -> expression
    }

    private fun normalizeExpressions(
        patternExpr: KtExpression?,
        codeExpr: KtExpression?,
        returnExpr: Boolean
    ): Pair<KtExpression?, KtExpression?> {
        val normalizedExpr = if (returnExpr) normalizeExpressionRet(patternExpr) else normalizeExpression(patternExpr)
        val normalizedCodeExpr = if (returnExpr) normalizeExpressionRet(codeExpr) else normalizeExpression(codeExpr)

        return when {
            normalizedExpr is KtBlockExpression || normalizedCodeExpr is KtBlockExpression -> patternExpr to codeExpr
            else -> normalizedExpr to normalizedCodeExpr
        }
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        val other = getTreeElementDepar<KtNamedFunction>() ?: return
        val (patternBody, codeBody) = normalizeExpressions(function.bodyBlockExpression, other.bodyBlockExpression, true)

        val bodyHandler = patternBody?.let(::getHandler)
        val bodyMatch = when {
            patternBody is KtNameReferenceExpression && codeBody == null -> bodyHandler is SubstitutionHandler
                    && bodyHandler.minOccurs <= 1 && bodyHandler.maxOccurs >= 1
                    && myMatchingVisitor.match(patternBody, other.bodyExpression)
            patternBody is KtNameReferenceExpression -> myMatchingVisitor.match(
                function.bodyBlockExpression,
                other.bodyBlockExpression
            )
            patternBody == null && codeBody == null -> myMatchingVisitor.match(function.bodyExpression, other.bodyExpression)
            patternBody == null -> function.bodyExpression == null || codeBody !is KtBlockExpression && myMatchingVisitor.match(function.bodyExpression, codeBody)
            codeBody == null -> patternBody !is KtBlockExpression && myMatchingVisitor.match(patternBody, other.bodyExpression)
            else -> myMatchingVisitor.match(function.bodyBlockExpression, other.bodyBlockExpression)
        }
        myMatchingVisitor.result = myMatchingVisitor.match(function.modifierList, other.modifierList)
                && matchTextOrVariable(function.nameIdentifier, other.nameIdentifier)
                && myMatchingVisitor.match(function.typeParameterList, other.typeParameterList)
                && myMatchingVisitor.match(function.typeReference, other.typeReference)
                && myMatchingVisitor.match(function.valueParameterList, other.valueParameterList)
                && myMatchingVisitor.match(function.receiverTypeReference, other.receiverTypeReference)
                && bodyMatch

        function.nameIdentifier?.let { nameIdentifier ->
            val handler = getHandler(nameIdentifier)
            if (myMatchingVisitor.result && handler is SubstitutionHandler) {
                handler.handle(other.nameIdentifier, myMatchingVisitor.matchContext)
            }
        }
    }

    override fun visitModifierList(list: KtModifierList) {
        val other = getTreeElementDepar<KtModifierList>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchSonsInAnyOrder(list, other)
    }

    override fun visitIfExpression(expression: KtIfExpression) {
        val other = getTreeElementDepar<KtIfExpression>() ?: return
        val elseBranch = normalizeExpression(expression.`else`)
        myMatchingVisitor.result = myMatchingVisitor.match(expression.condition, other.condition)
                && myMatchingVisitor.matchNormalized(expression.then, other.then)
                && (elseBranch == null || myMatchingVisitor.matchNormalized(expression.`else`, other.`else`))
    }

    override fun visitForExpression(expression: KtForExpression) {
        val other = getTreeElementDepar<KtForExpression>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(expression.loopParameter, other.loopParameter)
                && myMatchingVisitor.match(expression.loopRange, other.loopRange)
                && myMatchingVisitor.matchNormalized(expression.body, other.body)
    }

    override fun visitWhileExpression(expression: KtWhileExpression) {
        val other = getTreeElementDepar<KtWhileExpression>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(expression.condition, other.condition)
                && myMatchingVisitor.matchNormalized(expression.body, other.body)
    }

    override fun visitDoWhileExpression(expression: KtDoWhileExpression) {
        val other = getTreeElementDepar<KtDoWhileExpression>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(expression.condition, other.condition)
                && myMatchingVisitor.matchNormalized(expression.body, other.body)
    }

    override fun visitWhenConditionInRange(condition: KtWhenConditionInRange) {
        val other = getTreeElementDepar<KtWhenConditionInRange>() ?: return
        myMatchingVisitor.result = condition.isNegated == other.isNegated
                && myMatchingVisitor.match(condition.rangeExpression, other.rangeExpression)
    }

    override fun visitWhenConditionIsPattern(condition: KtWhenConditionIsPattern) {
        val other = getTreeElementDepar<KtWhenConditionIsPattern>() ?: return
        myMatchingVisitor.result = condition.isNegated == other.isNegated
                && myMatchingVisitor.match(condition.typeReference, other.typeReference)
    }

    override fun visitWhenConditionWithExpression(condition: KtWhenConditionWithExpression) {
        val other = getTreeElementDepar<PsiElement>() ?: return
        val handler = getHandler(condition)
        if (handler is SubstitutionHandler) {
            myMatchingVisitor.result = handler.handle(other, myMatchingVisitor.matchContext)
        } else {
            myMatchingVisitor.result = other is KtWhenConditionWithExpression
                    && myMatchingVisitor.match(condition.expression, other.expression)
        }
    }

    override fun visitWhenEntry(ktWhenEntry: KtWhenEntry) {
        val other = getTreeElementDepar<KtWhenEntry>() ?: return

        // $x$ -> $y$ should match else branches
        val bypassElseTest = ktWhenEntry.firstChild is KtWhenConditionWithExpression
                && ktWhenEntry.firstChild.children.size == 1
                && ktWhenEntry.firstChild.firstChild is KtNameReferenceExpression

        myMatchingVisitor.result =
            (bypassElseTest && other.isElse || myMatchingVisitor.matchInAnyOrder(ktWhenEntry.conditions, other.conditions))
                    && myMatchingVisitor.match(ktWhenEntry.expression, other.expression)
                    && (bypassElseTest || ktWhenEntry.isElse == other.isElse)
    }

    override fun visitWhenExpression(expression: KtWhenExpression) {
        val other = getTreeElementDepar<KtWhenExpression>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(expression.subjectExpression, other.subjectExpression)
                && myMatchingVisitor.matchInAnyOrder(expression.entries, other.entries)
    }

    override fun visitFinallySection(finallySection: KtFinallySection) {
        val other = getTreeElementDepar<KtFinallySection>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(finallySection.finalExpression, other.finalExpression)
    }

    override fun visitCatchSection(catchClause: KtCatchClause) {
        val other = getTreeElementDepar<KtCatchClause>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(catchClause.parameterList, other.parameterList)
                && myMatchingVisitor.match(catchClause.catchBody, other.catchBody)
    }

    override fun visitTryExpression(expression: KtTryExpression) {
        val other = getTreeElementDepar<KtTryExpression>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(expression.tryBlock, other.tryBlock)
                && myMatchingVisitor.matchInAnyOrder(expression.catchClauses, other.catchClauses)
                && myMatchingVisitor.match(expression.finallyBlock, other.finallyBlock)
    }

    override fun visitTypeAlias(typeAlias: KtTypeAlias) {
        val other = getTreeElementDepar<KtTypeAlias>() ?: return
        myMatchingVisitor.result = matchTextOrVariable(typeAlias.nameIdentifier, other.nameIdentifier)
                && myMatchingVisitor.match(typeAlias.getTypeReference(), other.getTypeReference())
                && myMatchingVisitor.matchInAnyOrder(typeAlias.annotationEntries, other.annotationEntries)
        val handler = getHandler(typeAlias.nameIdentifier!!)
        if (myMatchingVisitor.result && handler is SubstitutionHandler) {
            handler.handle(other.nameIdentifier, myMatchingVisitor.matchContext)
        }
    }

    override fun visitConstructorCalleeExpression(constructorCalleeExpression: KtConstructorCalleeExpression) {
        val other = getTreeElementDepar<KtConstructorCalleeExpression>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(
            constructorCalleeExpression.constructorReferenceExpression, other.constructorReferenceExpression
        )
    }

    override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
        val other = getTreeElementDepar<KtAnnotationEntry>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(annotationEntry.calleeExpression, other.calleeExpression)
                && myMatchingVisitor.match(annotationEntry.typeArgumentList, other.typeArgumentList)
                && annotationEntry.matchArguments(other)
                && matchTextOrVariable(annotationEntry.useSiteTarget, other.useSiteTarget)
    }

    override fun visitAnnotatedExpression(expression: KtAnnotatedExpression) {
        myMatchingVisitor.result = when (val other = myMatchingVisitor.element) {
            is KtAnnotatedExpression -> myMatchingVisitor.match(expression.baseExpression, other.baseExpression)
                    && myMatchingVisitor.matchInAnyOrder(expression.annotationEntries, other.annotationEntries)
            else -> myMatchingVisitor.match(expression.baseExpression, other) && expression.annotationEntries.all {
                val handler = getHandler(it); handler is SubstitutionHandler && handler.minOccurs == 0
            }
        }
    }

    override fun visitProperty(property: KtProperty) {
        val other = getTreeElementDepar<KtProperty>() ?: return
        val handler = getHandler(property.nameIdentifier!!)
        myMatchingVisitor.result = (
                property.isVar == other.isVar || (handler is SubstitutionHandler && handler.findPredicate(KotlinAlsoMatchValVarPredicate::class.java) != null)
                ) && myMatchingVisitor.match(property.typeReference, other.typeReference)
                && myMatchingVisitor.match(property.modifierList, other.modifierList)
                && matchTextOrVariable(property.nameIdentifier, other.nameIdentifier)
                && myMatchingVisitor.match(property.docComment, other.docComment)
                && myMatchingVisitor.matchOptionally(property.delegateExpressionOrInitializer, other.delegateExpressionOrInitializer)
                && myMatchingVisitor.match(property.getter, other.getter)
                && myMatchingVisitor.match(property.setter, other.setter)
                && myMatchingVisitor.match(property.receiverTypeReference, other.receiverTypeReference)

        if (myMatchingVisitor.result && handler is SubstitutionHandler) {
            handler.handle(other.nameIdentifier, myMatchingVisitor.matchContext)
        }
    }

    override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
        val other = getTreeElementDepar<KtPropertyAccessor>() ?: return
        val accessorBody = if (accessor.hasBlockBody()) accessor.bodyBlockExpression else accessor.bodyExpression
        val otherBody = if (other.hasBlockBody()) other.bodyBlockExpression else other.bodyExpression
        myMatchingVisitor.result = myMatchingVisitor.match(accessor.modifierList, other.modifierList)
                && myMatchingVisitor.matchNormalized(accessorBody, otherBody, true)
    }

    override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
        val other = getTreeElementDepar<KtStringTemplateExpression>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchSequentially(expression.entries, other.entries)
    }

    override fun visitSimpleNameStringTemplateEntry(entry: KtSimpleNameStringTemplateEntry) {
        val other = getTreeElement<KtStringTemplateEntry>() ?: return
        val handler = getHandler(entry)
        if (handler is SubstitutionHandler) {
            myMatchingVisitor.result = handler.handle(other, myMatchingVisitor.matchContext)
            return
        }
        myMatchingVisitor.result = when (other) {
            is KtSimpleNameStringTemplateEntry, is KtBlockStringTemplateEntry ->
                myMatchingVisitor.match(entry.expression, other.expression)
            else -> false
        }
    }

    override fun visitLiteralStringTemplateEntry(entry: KtLiteralStringTemplateEntry) {
        val other = myMatchingVisitor.element
        myMatchingVisitor.result = when (val handler = entry.getUserData(CompiledPattern.HANDLER_KEY)) {
            is LiteralWithSubstitutionHandler -> handler.match(entry, other, myMatchingVisitor.matchContext)
            else -> substituteOrMatchText(entry, other)
        }
    }

    override fun visitBlockStringTemplateEntry(entry: KtBlockStringTemplateEntry) {
        val other = getTreeElementDepar<KtBlockStringTemplateEntry>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(entry.expression, other.expression)
    }

    override fun visitEscapeStringTemplateEntry(entry: KtEscapeStringTemplateEntry) {
        val other = getTreeElementDepar<KtEscapeStringTemplateEntry>() ?: return
        myMatchingVisitor.result = substituteOrMatchText(entry, other)
    }

    override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS) {
        val other = getTreeElementDepar<KtBinaryExpressionWithTypeRHS>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(expression.operationReference, other.operationReference)
                && myMatchingVisitor.match(expression.left, other.left)
                && myMatchingVisitor.match(expression.right, other.right)
    }

    override fun visitIsExpression(expression: KtIsExpression) {
        val other = getTreeElementDepar<KtIsExpression>() ?: return
        myMatchingVisitor.result = expression.isNegated == other.isNegated
                && myMatchingVisitor.match(expression.leftHandSide, other.leftHandSide)
                && myMatchingVisitor.match(expression.typeReference, other.typeReference)
    }

    override fun visitDestructuringDeclaration(destructuringDeclaration: KtDestructuringDeclaration) {
        val other = getTreeElementDepar<KtDestructuringDeclaration>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchSequentially(destructuringDeclaration.entries, other.entries)
                && myMatchingVisitor.match(destructuringDeclaration.initializer, other.initializer)
                && myMatchingVisitor.match(destructuringDeclaration.docComment, other.docComment)
    }

    override fun visitDestructuringDeclarationEntry(multiDeclarationEntry: KtDestructuringDeclarationEntry) {
        val other = getTreeElementDepar<KtDestructuringDeclarationEntry>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(multiDeclarationEntry.typeReference, other.typeReference)
                && myMatchingVisitor.match(multiDeclarationEntry.modifierList, other.modifierList)
                && multiDeclarationEntry.isVar == other.isVar
                && matchTextOrVariable(multiDeclarationEntry.nameIdentifier, other.nameIdentifier)
    }

    override fun visitThrowExpression(expression: KtThrowExpression) {
        val other = getTreeElementDepar<KtThrowExpression>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(expression.referenceExpression(), other.referenceExpression())
    }

    override fun visitClassLiteralExpression(expression: KtClassLiteralExpression) {
        val other = getTreeElementDepar<KtClassLiteralExpression>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(expression.receiverExpression, other.receiverExpression)
    }

    override fun visitComment(comment: PsiComment) {
        val other = getTreeElementDepar<PsiComment>() ?: return
        when (val handler = comment.getUserData(CompiledPattern.HANDLER_KEY)) {
            is LiteralWithSubstitutionHandler -> {
                if (other is KDocImpl) {
                    myMatchingVisitor.result = handler.match(comment, other, myMatchingVisitor.matchContext)
                } else {
                    val offset = 2 + other.text.substring(2).indexOfFirst { it > ' ' }
                    myMatchingVisitor.result = handler.match(other, getCommentText(other), offset, myMatchingVisitor.matchContext)
                }
            }
            is SubstitutionHandler -> {
                handler.findPredicate(RegExpPredicate::class.java)?.let {
                    it.setNodeTextGenerator { comment -> getCommentText(comment as PsiComment) }
                }
                myMatchingVisitor.result = handler.handle(
                    other,
                    2,
                    other.textLength - if (other.tokenType == KtTokens.EOL_COMMENT) 0 else 2,
                    myMatchingVisitor.matchContext
                )
            }
            else -> myMatchingVisitor.result = myMatchingVisitor.matchText(
                MatchUtil.normalize(getCommentText(comment)),
                MatchUtil.normalize(getCommentText(other))
            )
        }
    }

    override fun visitKDoc(kDoc: KDoc) {
        val other = getTreeElementDepar<KDoc>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchInAnyOrder(
            kDoc.getChildrenOfType<KDocSection>(),
            other.getChildrenOfType<KDocSection>()
        )
    }

    override fun visitKDocSection(section: KDocSection) {
        val other = getTreeElementDepar<KDocSection>() ?: return

        val important: (PsiElement) -> Boolean = {
            it.elementType != KDocTokens.LEADING_ASTERISK
                    && !(it.elementType == KDocTokens.TEXT && it.text.trim().isEmpty())
        }

        myMatchingVisitor.result = myMatchingVisitor.matchInAnyOrder(
            section.allChildren.filter(important).toList(),
            other.allChildren.filter(important).toList()
        )
    }

    override fun visitKDocTag(tag: KDocTag) {
        val other = getTreeElementDepar<KDocTag>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchInAnyOrder(tag.getChildrenOfType(), other.getChildrenOfType())
    }

    override fun visitKDocLink(link: KDocLink) {
        val other = getTreeElementDepar<KDocLink>() ?: return
        myMatchingVisitor.result = substituteOrMatchText(link, other)
    }
}