// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.fe10.binding

import com.intellij.lang.ASTNode
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.realPsi
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.idea.fir.fe10.FE10BindingContext
import org.jetbrains.kotlin.idea.fir.fe10.FirWeakReference
import org.jetbrains.kotlin.idea.fir.fe10.toDeclarationDescriptor
import org.jetbrains.kotlin.idea.fir.fe10.toKotlinType
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitClassReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.Receiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.utils.addToStdlib.safeAs


internal sealed class Fe10WrapperCall<F : FirQualifiedAccessExpression>(
    val firAccessExpression: FirWeakReference<F>,
    val context: FE10BindingContext
) : Call {
    override fun getExplicitReceiver(): Receiver? = firAccessExpression.withFir { it.explicitReceiver.toExpressionReceiverValue(context) }

    // this is not null only for CallForImplicitInvoke
    override fun getDispatchReceiver(): ReceiverValue? = null
}

internal class FunctionFe10WrapperCall(
    private val ktCall: KtCallExpression,
    firCall: FirWeakReference<FirFunctionCall>,
    context: FE10BindingContext
) : Fe10WrapperCall<FirFunctionCall>(firCall, context) {
    override fun getCallOperationNode(): ASTNode = ktCall.node

    override fun getCalleeExpression(): KtExpression? = ktCall.calleeExpression

    override fun getValueArgumentList(): KtValueArgumentList? = ktCall.valueArgumentList

    override fun getValueArguments(): List<ValueArgument> = ktCall.valueArguments

    override fun getFunctionLiteralArguments(): List<LambdaArgument> = ktCall.lambdaArguments

    override fun getTypeArguments(): List<KtTypeProjection> = ktCall.typeArguments

    override fun getTypeArgumentList(): KtTypeArgumentList? = ktCall.typeArgumentList

    override fun getCallElement(): KtElement = ktCall

    override fun getCallType(): Call.CallType = Call.CallType.DEFAULT
}

/**
 * created based on [org.jetbrains.kotlin.resolve.calls.util.CallMaker.makePropertyCall]
 */
internal class VariableFe10WrapperCall(
    private val referenceExpression: KtNameReferenceExpression,
    propertyCall: FirWeakReference<FirPropertyAccessExpression>,
    context: FE10BindingContext
) : Fe10WrapperCall<FirPropertyAccessExpression>(propertyCall, context) {
    override fun getCallOperationNode(): ASTNode? = referenceExpression.parent.safeAs<KtQualifiedExpression>()?.operationTokenNode

    override fun getCalleeExpression(): KtExpression = referenceExpression
    override fun getCallElement(): KtElement = referenceExpression

    override fun getValueArgumentList(): KtValueArgumentList? = null
    override fun getValueArguments(): List<ValueArgument> = emptyList()
    override fun getFunctionLiteralArguments(): List<LambdaArgument> = emptyList()

    override fun getTypeArguments(): List<KtTypeProjection> = emptyList()
    override fun getTypeArgumentList(): KtTypeArgumentList? = null

    override fun getCallType(): Call.CallType = Call.CallType.DEFAULT
}

@OptIn(SymbolInternals::class)
internal fun FirExpression?.toExpressionReceiverValue(context: FE10BindingContext): ReceiverValue? {
    if (this == null) return null

    if (this is FirThisReceiverExpression) {
        val ktClassSymbol =
            context.ktAnalysisSessionFacade.buildClassLikeSymbol(calleeReference.boundSymbol?.fir as FirClassLikeDeclaration)
        return ImplicitClassReceiver(ktClassSymbol.toDeclarationDescriptor(context) as ClassDescriptor)
    }
    val expression = realPsi.safeAs<KtExpression>() ?: context.implementationPostponed()
    return ExpressionReceiver.create(
        expression,
        context.ktAnalysisSessionFacade.buildKtType(typeRef).toKotlinType(context),
        context.incorrectImplementation { BindingContext.EMPTY }
    )
}
