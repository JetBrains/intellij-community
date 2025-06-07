// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInsight.Nullability
import com.intellij.codeInspection.dataFlow.TypeConstraint
import com.intellij.codeInspection.dataFlow.TypeConstraints
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.java.inst.*
import com.intellij.codeInspection.dataFlow.jvm.SpecialField
import com.intellij.codeInspection.dataFlow.jvm.TrapTracker
import com.intellij.codeInspection.dataFlow.jvm.transfer.*
import com.intellij.codeInspection.dataFlow.jvm.transfer.TryCatchTrap.CatchClauseDescriptor
import com.intellij.codeInspection.dataFlow.lang.ir.*
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.ControlFlowOffset
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet
import com.intellij.codeInspection.dataFlow.types.*
import com.intellij.codeInspection.dataFlow.value.*
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue.TransferTarget
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue.Trap
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.*
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.MethodSignatureUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.FList
import com.siyeh.ig.psiutils.TypeUtils
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.builtins.getReturnTypeFromFunctionType
import org.jetbrains.kotlin.contracts.description.CallsEffectDeclaration
import org.jetbrains.kotlin.contracts.description.ContractProviderKey
import org.jetbrains.kotlin.contracts.description.EventOccurrencesRange
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeInsight.hints.RangeKtExpressionType.*
import org.jetbrains.kotlin.idea.core.resolveType
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinAnchor.*
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinProblem.*
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.targetLoop
import org.jetbrains.kotlin.idea.project.builtIns
import org.jetbrains.kotlin.idea.refactoring.move.moveMethod.type
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.getRangeBinaryExpressionType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.kotlin.toSourceElement
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.bindingContextUtil.getTargetFunction
import org.jetbrains.kotlin.resolve.calls.model.ExpressionValueArgument
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.VarargValueArgument
import org.jetbrains.kotlin.resolve.constants.IntegerLiteralTypeConstructor
import org.jetbrains.kotlin.resolve.constants.TypedCompileTimeConstant
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.isInlineClass
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitReceiver
import org.jetbrains.kotlin.resolve.source.KotlinSourceElement
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.*
import java.util.concurrent.ConcurrentHashMap

class KtControlFlowBuilder(val factory: DfaValueFactory, val context: KtExpression) {
    private val flow = ControlFlow(factory, context)
    private val trapTracker = TrapTracker(factory, KtClassDef.typeConstraintFactory(context))
    private val stringType = KtClassDef.getClassConstraint(context, StandardNames.FqNames.string)
    private var broken: Boolean = false

    fun buildFlow(): ControlFlow? {
        processExpression(context)
        if (broken) return null
        addInstruction(PopInstruction()) // return value
        flow.finish()
        return flow
    }

    private fun processExpression(expr: KtExpression?) {
        if (expr == null) {
            pushUnknown()
            return
        }
        flow.startElement(expr)
        if (!processConstant(expr)) {
            when (expr) {
                is KtBlockExpression -> processBlock(expr)
                is KtParenthesizedExpression -> processExpression(expr.expression)
                is KtBinaryExpression -> processBinaryExpression(expr)
                is KtBinaryExpressionWithTypeRHS -> processAsExpression(expr)
                is KtPrefixExpression -> processPrefixExpression(expr)
                is KtPostfixExpression -> processPostfixExpression(expr)
                is KtIsExpression -> processIsExpression(expr)
                is KtCallExpression -> processCallExpression(expr)
                is KtConstantExpression -> processConstantExpression(expr)
                is KtSimpleNameExpression -> processReferenceExpression(expr)
                is KtDotQualifiedExpression -> processQualifiedReferenceExpression(expr)
                is KtSafeQualifiedExpression -> processQualifiedReferenceExpression(expr)
                is KtReturnExpression -> processReturnExpression(expr)
                is KtContinueExpression -> processLabeledJumpExpression(expr)
                is KtBreakExpression -> processLabeledJumpExpression(expr)
                is KtThrowExpression -> processThrowExpression(expr)
                is KtIfExpression -> processIfExpression(expr)
                is KtWhenExpression -> processWhenExpression(expr)
                is KtWhileExpression -> processWhileExpression(expr)
                is KtDoWhileExpression -> processDoWhileExpression(expr)
                is KtForExpression -> processForExpression(expr)
                is KtProperty -> processDeclaration(expr)
                is KtLambdaExpression -> processLambda(expr)
                is KtStringTemplateExpression -> processStringTemplate(expr)
                is KtArrayAccessExpression -> processArrayAccess(expr)
                is KtAnnotatedExpression -> processExpression(expr.baseExpression)
                is KtClassLiteralExpression -> processClassLiteralExpression(expr)
                is KtLabeledExpression -> processExpression(expr.baseExpression)
                is KtThisExpression -> processThisExpression(expr)
                is KtSuperExpression -> pushUnknown()
                is KtCallableReferenceExpression -> processCallableReference(expr)
                is KtTryExpression -> processTryExpression(expr)
                is KtDestructuringDeclaration -> processDestructuringDeclaration(expr)
                is KtObjectLiteralExpression -> processObjectLiteral(expr)
                is KtNamedFunction -> processCodeDeclaration(expr)
                is KtClass -> processCodeDeclaration(expr)
                else -> {
                    // unsupported construct
                    if (LOG.isDebugEnabled) {
                        val className = expr.javaClass.name
                        if (unsupported.add(className)) {
                            LOG.debug("Unsupported expression in control flow: $className")
                        }
                    }
                    broken = true
                }
            }
        }
        flow.finishElement(expr)
    }

    private fun processConstant(expr: KtExpression?): Boolean {
        expr ?: return false
        val context = expr.analyze(BodyResolveMode.PARTIAL)
        val constant = ConstantExpressionEvaluator.getConstant(expr, context) as? TypedCompileTimeConstant<*> ?: return false
        val declaredType = expr.getKotlinType() ?: return false
        // Unsigned type handling is not supported yet
        if (declaredType.isUnsignedNumberType()) return false
        val value = constant.getValue(declaredType) ?: return false
        if (value !is Int && value !is Long && value !is Float && value !is Double && value !is String) return false
        val actualType = constant.type
        val dfConstantType = DfTypes.constant(value, actualType.toDfType())
        addInstruction(PushValueInstruction(dfConstantType, KotlinExpressionAnchor(expr)))
        addImplicitConversion(actualType, declaredType)
        return true
    }

    private fun processCodeDeclaration(expr: KtExpression) {
        processEscapes(expr)
        pushUnknown()
    }

    private fun processObjectLiteral(expr: KtObjectLiteralExpression) {
        processEscapes(expr)
        for (superTypeListEntry in expr.objectDeclaration.superTypeListEntries) {
            if (superTypeListEntry is KtSuperTypeCallEntry) {
                // super-constructor call: may be impure
                addInstruction(FlushFieldsInstruction())
            }
        }
        val dfType = expr.getKotlinType().toDfType()
        addInstruction(PushValueInstruction(dfType, KotlinExpressionAnchor(expr)))
    }

    private fun processDestructuringDeclaration(expr: KtDestructuringDeclaration) {
        processExpression(expr.initializer)
        for (entry in expr.entries) {
            addInstruction(FlushVariableInstruction(factory.varFactory.createVariableValue(KtVariableDescriptor(entry))))
        }
    }

    data class KotlinCatchClauseDescriptor(val clause : KtCatchClause): CatchClauseDescriptor {
        override fun parameter(): VariableDescriptor? {
            val parameter = clause.catchParameter ?: return null
            return KtVariableDescriptor(parameter)
        }

        override fun constraints(): MutableList<TypeConstraint> {
            val parameter = clause.catchParameter ?: return mutableListOf()
            return mutableListOf(TypeConstraint.fromDfType(parameter.type().toDfType()))
        }
    }

    private fun processTryExpression(statement: KtTryExpression) {
        inlinedBlock(statement) {
            val tryBlock = statement.tryBlock
            val finallyBlock = statement.finallyBlock
            val finallyStart = DeferredOffset()
            val finallyDescriptor = if (finallyBlock != null) EnterFinallyTrap(finallyBlock, finallyStart) else null
            finallyDescriptor?.let { trapTracker.pushTrap(it) }

            val kotlinType = statement.getKotlinType()
            val tempVar = flow.createTempVariable(kotlinType.toDfType())
            val sections = statement.catchClauses
            val clauses = LinkedHashMap<CatchClauseDescriptor, DeferredOffset>()
            if (sections.isNotEmpty()) {
                for (section in sections) {
                    val catchBlock = section.catchBody
                    if (catchBlock != null) {
                        clauses[KotlinCatchClauseDescriptor(section)] = DeferredOffset()
                    }
                }
                trapTracker.pushTrap(TryCatchTrap(statement, clauses))
            }

            processExpression(tryBlock)
            addImplicitConversion(tryBlock, kotlinType)
            addInstruction(JvmAssignmentInstruction(null, tempVar))

            val gotoEnd = createTransfer(statement, tryBlock, tempVar, true)
            val singleFinally = FList.createFromReversed<Trap>(ContainerUtil.createMaybeSingletonList(finallyDescriptor))
            controlTransfer(gotoEnd, singleFinally)

            if (sections.isNotEmpty()) {
                trapTracker.popTrap(TryCatchTrap::class.java)
            }

            for (section in sections) {
                val offset = clauses[KotlinCatchClauseDescriptor(section)]
                if (offset == null) continue
                setOffset(offset)
                val catchBlock = section.catchBody
                processExpression(catchBlock)
                addImplicitConversion(catchBlock, kotlinType)
                addInstruction(JvmAssignmentInstruction(null, tempVar))
                controlTransfer(gotoEnd, singleFinally)
            }

            if (finallyBlock != null) {
                setOffset(finallyStart)
                trapTracker.popTrap(EnterFinallyTrap::class.java)
                trapTracker.pushTrap(InsideFinallyTrap(finallyBlock))
                processExpression(finallyBlock.finalExpression)
                addInstruction(PopInstruction())
                controlTransfer(ExitFinallyTransfer(finallyDescriptor!!), FList.emptyList())
                trapTracker.popTrap(InsideFinallyTrap::class.java)
            }
        }
    }

    private fun processCallableReference(expr: KtCallableReferenceExpression) {
        processExpression(expr.receiverExpression)
        addInstruction(KotlinCallableReferenceInstruction(expr, expr.getKotlinType().toDfType()))
    }

    private fun processThisExpression(expr: KtThisExpression) {
        val exprType = expr.getKotlinType()
        val bindingContext = expr.safeAnalyzeNonSourceRootCode(BodyResolveMode.FULL)
        // Might differ from expression type if smartcast occurred
        val thisType = bindingContext[BindingContext.EXPRESSION_TYPE_INFO, expr.instanceReference]?.type
        val dfType = thisType.toDfType()
        val descriptor = bindingContext[BindingContext.REFERENCE_TARGET, expr.instanceReference]
        if (descriptor != null && thisType != null) {
            val function = (descriptor.toSourceElement as? KotlinSourceElement)?.psi as? KtFunctionLiteral
            val varDesc = if (function != null) {
                KtLambdaSpecialVariableDescriptor(function, LambdaVariableKind.THIS, thisType)
            } else {
                KtThisDescriptor(descriptor, dfType)
            }
            addInstruction(PushInstruction(factory.varFactory.createVariableValue(varDesc), KotlinExpressionAnchor(expr)))
            addImplicitConversion(thisType, exprType)
        } else {
            addInstruction(PushValueInstruction(dfType, KotlinExpressionAnchor(expr)))
        }
    }

    private fun processClassLiteralExpression(expr: KtClassLiteralExpression) {
        val kotlinType = expr.getKotlinType()
        val receiver = expr.receiverExpression
        if (kotlinType != null) {
            if (receiver is KtSimpleNameExpression && receiver.mainReference.resolve() is KtClass) {
                val arguments = kotlinType.arguments
                if (arguments.size == 1) {
                    val kType = arguments[0].type
                    val kClassPsiType = TypeConstraint.fromDfType(kotlinType.toDfType())
                    if (kClassPsiType != TypeConstraints.TOP) {
                        val kClassConstant: DfType = DfTypes.referenceConstant(kType, kClassPsiType)
                        addInstruction(PushValueInstruction(kClassConstant, KotlinExpressionAnchor(expr)))
                        return
                    }
                }
            }
        }
        processExpression(receiver)
        addInstruction(PopInstruction())
        addInstruction(PushValueInstruction(kotlinType.toDfType()))
        // TODO: support kotlin-class as a variable; link to TypeConstraint
    }

    private fun processAsExpression(expr: KtBinaryExpressionWithTypeRHS) {
        val operand = expr.left
        val typeReference = expr.right
        val type = getTypeCheckDfType(typeReference)
        val ref = expr.operationReference
        if (ref.text != "as?" && ref.text != "as") {
            broken = true
            return
        }
        processExpression(operand)
        if (type == DfType.TOP) {
            // Unknown/generic type: we cannot evaluate
            addInstruction(EvalUnknownInstruction(KotlinExpressionAnchor(expr), 1, expr.getKotlinType().toDfType()))
            return
        }
        val operandType = operand.getKotlinType()
        val operandDfType = operandType.toDfType()
        if (operandDfType is DfPrimitiveType) {
            addInstruction(WrapDerivedVariableInstruction(DfTypes.NOT_NULL_OBJECT, SpecialField.UNBOX))
        }
        else if (operandType?.constructor?.declarationDescriptor?.isInlineClass() == true &&
                 expr.getKotlinType()?.constructor?.declarationDescriptor?.isInlineClass() != true) {
            addInstruction(PopInstruction())
            addInstruction(PushValueInstruction(operandDfType))
        }
        if (ref.text == "as?") {
            val tempVariable: DfaVariableValue = flow.createTempVariable(DfTypes.OBJECT_OR_NULL)
            addInstruction(JvmAssignmentInstruction(null, tempVariable))
            addInstruction(PushValueInstruction(type, null))
            addInstruction(InstanceofInstruction(null, false))
            val offset = DeferredOffset()
            addInstruction(ConditionalGotoInstruction(offset, DfTypes.FALSE))
            val anchor = KotlinExpressionAnchor(expr)
            addInstruction(PushInstruction(tempVariable, anchor))
            val endOffset = DeferredOffset()
            addInstruction(GotoInstruction(endOffset))
            setOffset(offset)
            addInstruction(PushValueInstruction(DfTypes.NULL, anchor))
            setOffset(endOffset)
        } else {
            val transfer = trapTracker.maybeTransferValue("kotlin.ClassCastException")
            addInstruction(EnsureInstruction(KotlinCastProblem(operand, expr), RelationType.IS, type, transfer))
            if (typeReference != null) {
                val castType = typeReference.getAbbreviatedTypeOrType(typeReference.safeAnalyzeNonSourceRootCode(BodyResolveMode.FULL))
                if (castType.toDfType() is DfPrimitiveType) {
                    addInstruction(
                        GetQualifiedValueInstruction(SpecialField.UNBOX)
                    )
                }
            }
        }
    }

    private fun processArrayAccess(expr: KtArrayAccessExpression, storedValue: KtExpression? = null) {
        val arrayExpression = expr.arrayExpression
        processExpression(arrayExpression)
        val kotlinType = arrayExpression?.getKotlinType()
        var curType = kotlinType
        val indexes = expr.indexExpressions
        for (idx in indexes) {
            processExpression(idx)
            val lastIndex = idx == indexes.last()
            val anchor = if (lastIndex) {
                if (storedValue != null)
                    KotlinExpressionAnchor(PsiTreeUtil.findCommonParent(expr, storedValue) as? KtExpression ?: expr)
                else
                    KotlinExpressionAnchor(expr)
            }
            else null
            val expectedType = if (lastIndex) expr.getKotlinType()?.toDfType() ?: DfType.TOP else DfType.TOP
            var indexType = idx.getKotlinType()
            val constructor = indexType?.constructor as? IntegerLiteralTypeConstructor
            if (constructor != null) {
                indexType = constructor.getApproximatedType()
            }
            if (indexType == null || !indexType.fqNameEquals("kotlin.Int")) {
                if (lastIndex && storedValue != null) {
                    processUnknownArrayStore(storedValue)
                } else {
                    addInstruction(EvalUnknownInstruction(anchor, 2, expectedType))
                    addInstruction(FlushFieldsInstruction())
                }
                continue
            }
            if (curType != null && KotlinBuiltIns.isArrayOrPrimitiveArray(curType)) {
                if (indexType.canBeNull()) {
                    addInstruction(
                        GetQualifiedValueInstruction(SpecialField.UNBOX)
                    )
                }
                val transfer = trapTracker.maybeTransferValue("kotlin.IndexOutOfBoundsException")
                val elementType = expr.builtIns.getArrayElementType(curType)
                if (lastIndex && storedValue != null) {
                    processExpression(storedValue)
                    addImplicitConversion(storedValue.getKotlinType(), curType.getArrayElementType())
                    addInstruction(ArrayStoreInstruction(anchor, KotlinArrayIndexProblem(SpecialField.ARRAY_LENGTH, idx), transfer, null))
                } else {
                    addInstruction(ArrayAccessInstruction(anchor, KotlinArrayIndexProblem(SpecialField.ARRAY_LENGTH, idx), transfer, null))
                    addImplicitConversion(curType.getArrayElementType(), elementType)
                }
                curType = elementType
            } else {
                when {
                    KotlinBuiltIns.isString(kotlinType) -> {
                        if (indexType.canBeNull()) {
                            addInstruction(
                                GetQualifiedValueInstruction(
                                SpecialField.UNBOX)
                            )
                        }
                        val transfer = trapTracker.maybeTransferValue("kotlin.IndexOutOfBoundsException")
                        addInstruction(EnsureIndexInBoundsInstruction(KotlinArrayIndexProblem(SpecialField.STRING_LENGTH, idx), transfer))
                        if (lastIndex && storedValue != null) {
                            processExpression(storedValue)
                            addInstruction(PopInstruction())
                        }
                        addInstruction(PushValueInstruction(DfTypes.typedObject(PsiTypes.charType(), Nullability.UNKNOWN), anchor))
                    }
                    isList(kotlinType) -> {
                        if (indexType.canBeNull()) {
                            addInstruction(
                                GetQualifiedValueInstruction(
                                SpecialField.UNBOX)
                            )
                        }
                        val transfer = trapTracker.maybeTransferValue("kotlin.IndexOutOfBoundsException")
                        addInstruction(EnsureIndexInBoundsInstruction(KotlinArrayIndexProblem(SpecialField.COLLECTION_SIZE, idx), transfer))
                        if (lastIndex && storedValue != null) {
                            processExpression(storedValue)
                            addInstruction(PopInstruction())
                        }
                        pushUnknown()
                    }
                    else -> {
                        if (lastIndex && storedValue != null) {
                            processUnknownArrayStore(storedValue)
                        } else {
                            addCall(expr, 2)
                        }
                    }
                }
            }
        }
    }

    private fun processUnknownArrayStore(storedValue: KtExpression) {
        // stack before: <array_expression> <index_expression>
        processExpression(storedValue)
        val parent = storedValue.parent
        assert(parent is KtExpression) { "parent must be assignment expression, got ${parent::class}" }
        // process a[b] = c like a call with arguments a, b, c.
        addCall(parent as KtExpression, 3)
    }

    private fun isList(kotlinType: KotlinType?) =
        kotlinType != null && (KotlinBuiltIns.isListOrNullableList(kotlinType) ||
                kotlinType.supertypes().any { type -> KotlinBuiltIns.isListOrNullableList(type) })

    private fun processIsExpression(expr: KtIsExpression) {
        processExpression(expr.leftHandSide)
        val type = getTypeCheckDfType(expr.typeReference)
        if (type == DfType.TOP) {
            addInstruction(PopInstruction())
            pushUnknown()
        } else {
            addInstruction(PushValueInstruction(type))
            if (expr.isNegated) {
                addInstruction(InstanceofInstruction(null, false))
                addInstruction(NotInstruction(KotlinExpressionAnchor(expr)))
            } else {
                addInstruction(InstanceofInstruction(KotlinExpressionAnchor(expr), false))
            }
        }
    }

    private fun processStringTemplate(expr: KtStringTemplateExpression) {
        var first = true
        val entries = expr.entries
        if (entries.isEmpty()) {
            addInstruction(PushValueInstruction(DfTypes.referenceConstant("", stringType)))
            return
        }
        val lastEntry = entries.last()
        for (entry in entries) {
            when (entry) {
                is KtEscapeStringTemplateEntry ->
                    addInstruction(PushValueInstruction(DfTypes.referenceConstant(entry.unescapedValue, stringType)))
                is KtLiteralStringTemplateEntry ->
                    addInstruction(PushValueInstruction(DfTypes.referenceConstant(entry.text, stringType)))
                is KtStringTemplateEntryWithExpression ->
                    processExpression(entry.expression)
                else ->
                    pushUnknown()
            }
            if (!first) {
                val anchor = if (entry == lastEntry) KotlinExpressionAnchor(expr) else null
                addInstruction(StringConcatInstruction(anchor, stringType))
            }
            first = false
        }
        if (entries.size == 1 && entries[0] !is KtLiteralStringTemplateEntry) {
            // Implicit toString conversion for "$myVar" string
            addInstruction(PushValueInstruction(DfTypes.referenceConstant("", stringType)))
            addInstruction(StringConcatInstruction(KotlinExpressionAnchor(expr), stringType))
        }
    }

    private fun processLambda(expr: KtLambdaExpression) {
        val element = expr.bodyExpression
        if (element != null) {
            processEscapes(element)
            addInstruction(ClosureInstruction(listOf(element)))
        }
        pushUnknown()
    }

    private fun processEscapes(expr: KtExpression) {
        val vars = mutableSetOf<VariableDescriptor>()
        PsiTreeUtil.processElements(expr, KtSimpleNameExpression::class.java) { ref ->
            val value = KtVariableDescriptor.createFromSimpleName(factory, ref)
            if (value != null) {
                vars.add(value.descriptor)
            }
            return@processElements true
        }
        if (vars.isNotEmpty()) {
            addInstruction(EscapeInstruction(vars.toList()))
        }
    }

    private fun processCallExpression(expr: KtCallExpression, qualifierOnStack: Boolean = false) {
        val call = expr.resolveToCall()
        val updatedQualifierOnStack = if (!qualifierOnStack && call != null) {
            tryPushImplicitQualifier(call)
        } else {
            qualifierOnStack
        }
        var argCount: Int
        if (call != null) {
            argCount = pushResolvedCallArguments(call)
        } else {
            argCount = pushUnresolvedCallArguments(expr)
        }
        if (inlineKnownMethod(expr, argCount, updatedQualifierOnStack)) return
        val lambda = getInlineableLambda(expr)
        if (lambda != null) {
            if (updatedQualifierOnStack && inlineKnownLambdaCall(expr, lambda.lambda)) return
            val kind = getLambdaOccurrenceRange(expr, lambda.descriptor.original)
            inlineLambda(lambda.lambda, kind)
        } else {
            for (lambdaArg in expr.lambdaArguments) {
                processExpression(lambdaArg.getLambdaExpression())
                argCount++
            }
        }

        addCall(expr, argCount, updatedQualifierOnStack)
    }

    private fun tryPushImplicitQualifier(call: ResolvedCall<out CallableDescriptor>): Boolean {
        val receiver = call.dispatchReceiver
        if (receiver is ImplicitReceiver) {
            val descriptor = receiver.declarationDescriptor
            if (descriptor is DeclarationDescriptorWithSource) {
                val psi = descriptor.source.getPsi()
                if (psi is KtFunctionLiteral) {
                    val varDescriptor = KtLambdaSpecialVariableDescriptor(
                        psi, LambdaVariableKind.THIS,
                        receiver.type
                    )
                    addInstruction(PushInstruction(factory.varFactory.createVariableValue(varDescriptor), null))
                    return true
                }
            }
        }
        return false
    }

    private fun pushUnresolvedCallArguments(expr: KtCallExpression): Int {
        val args = expr.valueArgumentList?.arguments
        var argCount = 0
        if (args != null) {
            for (arg: KtValueArgument in args) {
                val argExpr = arg.getArgumentExpression()
                if (argExpr != null) {
                    processExpression(argExpr)
                    argCount++
                }
            }
        }
        return argCount
    }

    private fun pushResolvedCallArguments(call: ResolvedCall<out CallableDescriptor>): Int {
        val valueArguments = call.valueArguments
        var argCount = 0
        for ((descriptor, valueArg) in valueArguments) {
            when (valueArg) {
                is VarargValueArgument -> {
                    val arguments = valueArg.arguments
                    val singleArg = arguments.singleOrNull()
                    if (singleArg?.getSpreadElement() != null) {
                        processExpression(singleArg.getArgumentExpression())
                    } else {
                        for (arg in arguments) {
                            processExpression(arg.getArgumentExpression())
                        }
                        addInstruction(FoldArrayInstruction(null, descriptor.type.toDfType(), arguments.size))
                    }
                    argCount++
                }
                is ExpressionValueArgument -> {
                    val valueArgument = valueArg.valueArgument
                    if (valueArgument !is KtLambdaArgument) {
                        processExpression(valueArgument?.getArgumentExpression())
                        argCount++
                    }
                }
                else -> {
                    pushUnknown()
                    argCount++
                }
            }
        }
        return argCount
    }

    private fun inlineKnownMethod(expr: KtCallExpression, argCount: Int, qualifierOnStack: Boolean): Boolean {
        if (argCount == 0 && qualifierOnStack) {
            val descriptor = expr.resolveToCall()?.resultingDescriptor ?: return false
            val name = descriptor.name.asString()
            if (name == "isEmpty" || name == "isNotEmpty") {
                val containingDeclaration = descriptor.containingDeclaration
                val containingPackage = if (containingDeclaration is PackageFragmentDescriptor) containingDeclaration.fqName
                else (containingDeclaration as? ClassDescriptor)?.containingPackage()
                if (containingPackage?.asString() == "kotlin.collections") {
                    addInstruction(
                        GetQualifiedValueInstruction(
                        SpecialField.COLLECTION_SIZE)
                    )
                    addInstruction(PushValueInstruction(DfTypes.intValue(0)))
                    addInstruction(
                        BooleanBinaryInstruction(
                            if (name == "isEmpty") RelationType.EQ else RelationType.NE, false,
                            KotlinExpressionAnchor(expr)
                        )
                    )
                    val kotlinType = expr.getKotlinType()
                    if (kotlinType?.isMarkedNullable == true) {
                        addInstruction(WrapDerivedVariableInstruction(kotlinType.toDfType(), SpecialField.UNBOX))
                    }
                    return true
                }
            }
        }
        return false
    }

    private fun inlineKnownLambdaCall(expr: KtCallExpression, lambda: KtLambdaExpression): Boolean {
        // TODO: non-qualified methods (run, repeat)
        // TODO: collection methods (forEach, map, etc.)
        val resolvedCall = expr.resolveToCall() ?: return false
        val descriptor = resolvedCall.resultingDescriptor
        val packageFragment = descriptor.containingDeclaration as? PackageFragmentDescriptor ?: return false
        val bodyExpression = lambda.bodyExpression
        val receiver = (expr.parent as? KtQualifiedExpression)?.receiverExpression
        if (packageFragment.fqName.asString() == "kotlin" && resolvedCall.valueArguments.size == 1) {
            val name = descriptor.name.asString()
            if (name == "let" || name == "also" || name == "takeIf" || name == "takeUnless" || name == "apply" || name == "run") {
                val parameter = (if (name == "apply" || name == "run")
                    KtVariableDescriptor.getLambdaReceiver(factory, lambda)
                else
                    KtVariableDescriptor.getSingleLambdaParameter(factory, lambda)) ?: return false
                // qualifier is on stack
                val receiverType = receiver?.getKotlinType()
                val argType = if (expr.parent is KtSafeQualifiedExpression) receiverType?.makeNotNullable() else receiverType
                addImplicitConversion(receiver, argType)
                addInstruction(JvmAssignmentInstruction(null, parameter))
                when (name) {
                    "let", "run" -> {
                        addInstruction(PopInstruction())
                        val lambdaResultType = lambda.resolveType()?.getReturnTypeFromFunctionType()
                        val result = flow.createTempVariable(lambdaResultType.toDfType())
                        inlinedBlock(lambda) {
                            processExpression(bodyExpression)
                            flow.finishElement(lambda.functionLiteral)
                            addInstruction(JvmAssignmentInstruction(null, result))
                            addInstruction(PopInstruction())
                        }
                        addInstruction(PushInstruction(result, null))
                        addImplicitConversion(lambdaResultType, expr.getKotlinType())
                    }
                    "also", "apply" -> {
                        inlinedBlock(lambda) {
                            processExpression(bodyExpression)
                            flow.finishElement(lambda.functionLiteral)
                            addInstruction(PopInstruction())
                        }
                        addImplicitConversion(argType, expr.getKotlinType())
                        addInstruction(ResultOfInstruction(KotlinExpressionAnchor(expr)))
                    }
                    "takeIf", "takeUnless" -> {
                        val result = flow.createTempVariable(DfTypes.BOOLEAN)
                        inlinedBlock(lambda) {
                            processExpression(bodyExpression)
                            flow.finishElement(lambda.functionLiteral)
                            addInstruction(JvmAssignmentInstruction(null, result))
                            addInstruction(PopInstruction())
                        }
                        addInstruction(PushInstruction(result, null))
                        val offset = DeferredOffset()
                        addInstruction(ConditionalGotoInstruction(offset, DfTypes.booleanValue(name == "takeIf")))
                        addInstruction(PopInstruction())
                        addInstruction(PushValueInstruction(DfTypes.NULL))
                        val endOffset = DeferredOffset()
                        addInstruction(GotoInstruction(endOffset))
                        setOffset(offset)
                        addImplicitConversion(argType, expr.getKotlinType())
                        setOffset(endOffset)
                    }
                }
                addInstruction(ResultOfInstruction(KotlinExpressionAnchor(expr)))
                return true
            }
        }
        return false
    }

    private fun getLambdaOccurrenceRange(expr: KtCallExpression, descriptor: ValueParameterDescriptor): EventOccurrencesRange {
        val contractDescription = expr.resolveToCall()?.resultingDescriptor?.getUserData(ContractProviderKey)?.getContractDescription()
        if (contractDescription != null) {
            val callEffect = contractDescription.effects
                .singleOrNull { e -> e is CallsEffectDeclaration && e.variableReference.descriptor == descriptor }
                    as? CallsEffectDeclaration
            if (callEffect != null) {
                return callEffect.kind
            }
        }
        return EventOccurrencesRange.UNKNOWN
    }

    private fun inlineLambda(lambda: KtLambdaExpression, kind: EventOccurrencesRange) {
        /*
            We encode unknown call with inlineable lambda as
            unknownCode()
            while(condition1) {
              if(condition2) {
                lambda()
              }
              unknownCode()
            }
         */
        addInstruction(FlushFieldsInstruction())
        val offset = ControlFlow.FixedOffset(flow.instructionCount)
        val endOffset = DeferredOffset()
        if (kind != EventOccurrencesRange.EXACTLY_ONCE && kind != EventOccurrencesRange.MORE_THAN_ONCE &&
                kind != EventOccurrencesRange.AT_LEAST_ONCE) {
            pushUnknown()
            addInstruction(ConditionalGotoInstruction(endOffset, DfTypes.TRUE))
        }

        inlinedBlock(lambda) {
            val functionLiteral = lambda.functionLiteral
            val bodyExpression = lambda.bodyExpression
            if (bodyExpression != null) {
                val singleParameter = KtVariableDescriptor.getSingleLambdaParameter(factory, lambda)
                if (singleParameter != null) {
                    addInstruction(FlushVariableInstruction(singleParameter))
                } else {
                    for (parameter in lambda.valueParameters) {
                        flushParameter(parameter)
                    }
                }
                val receiver = KtVariableDescriptor.getLambdaReceiver(factory, lambda)
                if (receiver != null) {
                    addInstruction(FlushVariableInstruction(receiver))
                }
            }
            processExpression(bodyExpression)
            flow.finishElement(functionLiteral)
            addInstruction(PopInstruction())
        }

        setOffset(endOffset)
        addInstruction(FlushFieldsInstruction())
        if (kind != EventOccurrencesRange.AT_MOST_ONCE && kind != EventOccurrencesRange.EXACTLY_ONCE) {
            pushUnknown()
            addInstruction(ConditionalGotoInstruction(offset, DfTypes.TRUE))
        }
    }

    private inline fun inlinedBlock(element: KtElement, fn : () -> Unit) {
        // Transfer value is pushed to avoid emptying stack beyond this point
        trapTracker.pushTrap(InsideInlinedBlockTrap(element))
        addInstruction(PushInstruction(factory.controlTransfer(DfaControlTransferValue.RETURN_TRANSFER, FList.emptyList()), null))

        fn()

        trapTracker.popTrap(InsideInlinedBlockTrap::class.java)
        // Pop transfer value
        addInstruction(PopInstruction())
    }

    private fun addCall(expr: KtExpression, args: Int, qualifierOnStack: Boolean = false) {
        val transfer = trapTracker.maybeTransferValue("kotlin.Throwable")
        addInstruction(KotlinFunctionCallInstruction(expr, args, qualifierOnStack, transfer))
    }

    private fun processQualifiedReferenceExpression(expr: KtQualifiedExpression) {
        val receiver = expr.receiverExpression
        processExpression(receiver)
        val offset = DeferredOffset()
        if (expr is KtSafeQualifiedExpression) {
            val receiverType = receiver.getKotlinType()
            addImplicitConversion(receiverType, receiverType?.makeNullable())
            addInstruction(DupInstruction())
            addInstruction(ConditionalGotoInstruction(offset, DfTypes.NULL))
        }
        val selector = expr.selectorExpression
        selector?.let(flow::startElement)
        if (!pushJavaClassField(receiver, selector, expr)) {
            val specialField = findSpecialField(expr)
            if (specialField != null) {
                addInstruction(GetQualifiedValueInstruction(specialField))
                if (expr is KtSafeQualifiedExpression) {
                    addInstruction(WrapDerivedVariableInstruction(expr.getKotlinType().toDfType(), SpecialField.UNBOX))
                }
            } else {
                when (selector) {
                    is KtCallExpression -> processCallExpression(selector, true)
                    is KtSimpleNameExpression -> processReferenceExpression(selector, true)
                    else -> {
                        addInstruction(PopInstruction())
                        processExpression(selector)
                    }
                }
            }
            addInstruction(ResultOfInstruction(KotlinExpressionAnchor(expr)))
        }
        selector?.let(flow::finishElement)
        if (expr is KtSafeQualifiedExpression) {
            val endOffset = DeferredOffset()
            addInstruction(GotoInstruction(endOffset))
            setOffset(offset)
            addInstruction(PopInstruction())
            addInstruction(PushValueInstruction(DfTypes.NULL, KotlinExpressionAnchor(expr)))
            setOffset(endOffset)
        }
    }

    private fun pushJavaClassField(receiver: KtExpression, selector: KtExpression?, expr: KtQualifiedExpression): Boolean {
        if (selector == null || !selector.textMatches("java")) return false
        if (!receiver.getKotlinType().fqNameEquals("kotlin.reflect.KClass")) return false
        val kotlinType = expr.getKotlinType() ?: return false
        val classType = TypeConstraint.fromDfType(kotlinType.toDfType())
        if (!classType.isExact(CommonClassNames.JAVA_LANG_CLASS)) return false
        addInstruction(KotlinClassToJavaClassInstruction(KotlinExpressionAnchor(expr), classType))
        return true
    }

    private fun findSpecialField(type: KotlinType?): SpecialField? {
        type ?: return null
        return when {
            type.isEnum() -> SpecialField.ENUM_ORDINAL
            KotlinBuiltIns.isArray(type) || KotlinBuiltIns.isPrimitiveArray(type) -> SpecialField.ARRAY_LENGTH
            KotlinBuiltIns.isCollectionOrNullableCollection(type) ||
                    KotlinBuiltIns.isMapOrNullableMap(type) ||
                    type.supertypes().any { st -> KotlinBuiltIns.isCollectionOrNullableCollection(st) || KotlinBuiltIns.isMapOrNullableMap(st)}
                -> SpecialField.COLLECTION_SIZE
            KotlinBuiltIns.isStringOrNullableString(type) -> SpecialField.STRING_LENGTH
            else -> null
        }
    }

    private fun findSpecialField(expr: KtQualifiedExpression): SpecialField? {
        val selector = expr.selectorExpression ?: return null
        val receiver = expr.receiverExpression
        val selectorText = selector.text
        if (selectorText != "size" && selectorText != "length" && selectorText != "ordinal") return null
        val field = findSpecialField(receiver.getKotlinType()) ?: return null
        val expectedFieldName = if (field == SpecialField.ARRAY_LENGTH) "size" else field.toString()
        if (selectorText != expectedFieldName) return null
        return field
    }

    private fun processPrefixExpression(expr: KtPrefixExpression) {
        val operand = expr.baseExpression
        processExpression(operand)
        val anchor = KotlinExpressionAnchor(expr)
        if (operand != null) {
            val dfType = operand.getKotlinType().toDfType()
            val dfVar = KtVariableDescriptor.createFromQualified(factory, operand)
            val ref = expr.operationReference.text
            if (dfType is DfIntegralType) {
                when (ref) {
                    "++", "--" -> {
                        if (dfVar != null) {
                            addInstruction(PushValueInstruction(dfType.meetRange(LongRangeSet.point(1))))
                            addInstruction(NumericBinaryInstruction(if (ref == "++") LongRangeBinOp.PLUS else LongRangeBinOp.MINUS, null))
                            addInstruction(JvmAssignmentInstruction(anchor, dfVar))
                            return
                        }
                    }
                    "+" -> {
                        return
                    }
                    "-" -> {
                        addInstruction(PushValueInstruction(dfType.meetRange(LongRangeSet.point(0))))
                        addInstruction(SwapInstruction())
                        addInstruction(NumericBinaryInstruction(LongRangeBinOp.MINUS, anchor))
                        return
                    }
                }
            }
            if (dfType is DfBooleanType && ref == "!") {
                addInstruction(NotInstruction(anchor))
                return
            }
            if (dfVar != null && (ref == "++" || ref == "--")) {
                // Custom inc/dec may update the variable
                addInstruction(FlushVariableInstruction(dfVar))
            }
        }
        addInstruction(EvalUnknownInstruction(anchor, 1, expr.getKotlinType()?.toDfType() ?: DfType.TOP))
    }

    private fun processPostfixExpression(expr: KtPostfixExpression) {
        val operand = expr.baseExpression
        processExpression(operand)
        val anchor = KotlinExpressionAnchor(expr)
        val ref = expr.operationReference.text
        if (ref == "++" || ref == "--") {
            if (operand != null) {
                val dfType = operand.getKotlinType().toDfType()
                val dfVar = KtVariableDescriptor.createFromQualified(factory, operand)
                if (dfVar != null) {
                    if (dfType is DfIntegralType) {
                        addInstruction(DupInstruction())
                        addInstruction(PushValueInstruction(dfType.meetRange(LongRangeSet.point(1))))
                        addInstruction(NumericBinaryInstruction(if (ref == "++") LongRangeBinOp.PLUS else LongRangeBinOp.MINUS, null))
                        addInstruction(JvmAssignmentInstruction(anchor, dfVar))
                        addInstruction(PopInstruction())
                    } else {
                        // Custom inc/dec may update the variable
                        addInstruction(FlushVariableInstruction(dfVar))
                    }
                } else {
                    // Unknown value updated
                    addInstruction(FlushFieldsInstruction())
                }
            }
        } else if (ref == "!!") {
            val transfer: DfaControlTransferValue? = trapTracker.maybeTransferValue("kotlin.NullPointerException")
            val operandType = operand?.getKotlinType()
            if (operandType?.canBeNull() == true) {
                addInstruction(EnsureInstruction(KotlinNullCheckProblem(expr), RelationType.NE, DfTypes.NULL, transfer))
                // Probably unbox
                addImplicitConversion(operandType, expr.getKotlinType())
            }
        } else {
            addInstruction(EvalUnknownInstruction(anchor, 1, expr.getKotlinType()?.toDfType() ?: DfType.TOP))
        }
    }

    private fun processDoWhileExpression(expr: KtDoWhileExpression) {
        inlinedBlock(expr) {
            val offset = ControlFlow.FixedOffset(flow.instructionCount)
            processExpression(expr.body)
            addInstruction(PopInstruction())
            processExpression(expr.condition)
            addInstruction(ConditionalGotoInstruction(offset, DfTypes.TRUE))
            flow.finishElement(expr)
        }
        pushUnknown()
        addInstruction(FinishElementInstruction(expr))
    }

    private fun processWhileExpression(expr: KtWhileExpression) {
        inlinedBlock(expr) {
            val startOffset = ControlFlow.FixedOffset(flow.instructionCount)
            val condition = expr.condition
            processExpression(condition)
            val endOffset = DeferredOffset()
            addInstruction(ConditionalGotoInstruction(endOffset, DfTypes.FALSE, condition))
            processExpression(expr.body)
            addInstruction(PopInstruction())
            addInstruction(GotoInstruction(startOffset))
            setOffset(endOffset)
            flow.finishElement(expr)
        }
        pushUnknown()
        addInstruction(FinishElementInstruction(expr))
    }

    private fun processForExpression(expr: KtForExpression) {
        inlinedBlock(expr) {
            val parameter = expr.loopParameter
            if (parameter == null) {
                broken = true
                return@inlinedBlock
            }
            val parameterVar = factory.varFactory.createVariableValue(KtVariableDescriptor(parameter))
            val parameterType = parameter.type()
            val pushLoopCondition = processForRange(expr, parameterVar, parameterType)
            val startOffset = ControlFlow.FixedOffset(flow.instructionCount)
            val endOffset = DeferredOffset()
            flushParameter(parameter)
            pushLoopCondition()
            addInstruction(ConditionalGotoInstruction(endOffset, DfTypes.FALSE))
            processExpression(expr.body)
            addInstruction(PopInstruction())
            addInstruction(GotoInstruction(startOffset))
            setOffset(endOffset)
            flow.finishElement(expr)
        }
        pushUnknown()
        addInstruction(FinishElementInstruction(expr))
    }

    private fun flushParameter(parameter: KtParameter) {
        val destructuringDeclaration = parameter.destructuringDeclaration
        if (destructuringDeclaration != null) {
            for (entry in destructuringDeclaration.entries) {
                addInstruction(FlushVariableInstruction(factory.varFactory.createVariableValue(KtVariableDescriptor(entry))))
            }
        } else {
            addInstruction(FlushVariableInstruction(factory.varFactory.createVariableValue(KtVariableDescriptor(parameter))))
        }
    }

    private fun processForRange(expr: KtForExpression, parameterVar: DfaVariableValue, parameterType: KotlinType?): () -> Unit {
        val range = expr.loopRange
        if (parameterVar.dfType is DfIntegralType) {
            when (range) {
                is KtDotQualifiedExpression -> {
                    val selector = range.selectorExpression
                    val receiver = range.receiverExpression
                    if (selector != null && selector.textMatches("indices")) {
                        val kotlinType = receiver.getKotlinType()
                        if (kotlinType != null && !kotlinType.canBeNull()) {
                            val dfVar = KtVariableDescriptor.createFromSimpleName(factory, receiver)
                            if (dfVar != null) {
                                val sf = when {
                                    KotlinBuiltIns.isCollectionOrNullableCollection(kotlinType) ||
                                            kotlinType.supertypes().any { st -> KotlinBuiltIns.isCollectionOrNullableCollection(st) } -> SpecialField.COLLECTION_SIZE
                                    KotlinBuiltIns.isArrayOrPrimitiveArray(kotlinType) -> SpecialField.ARRAY_LENGTH
                                    else -> null
                                }
                                if (sf != null) {
                                    val size = sf.createValue(factory, dfVar)
                                    return rangeFunction(expr, parameterVar, factory.fromDfType(DfTypes.intValue(0)),
                                                         RelationType.GE, size, RelationType.LT)
                                }
                            }
                        }
                    }
                }
                is KtBinaryExpression -> {
                    val (leftRelation, rightRelation) =
                        range.getRangeBinaryExpressionType()?.getRelationType() ?: (null to null)
                    if (leftRelation != null && rightRelation != null) {
                        val left = range.left
                        val right = range.right
                        val leftType = left?.getKotlinType()
                        val rightType = right?.getKotlinType()
                        if (leftType.toDfType() is DfIntegralType && rightType.toDfType() is DfIntegralType) {
                            processExpression(left)
                            val leftVar = flow.createTempVariable(parameterVar.dfType)
                            addImplicitConversion(left, parameterType)
                            addInstruction(JvmAssignmentInstruction(null, leftVar))
                            addInstruction(PopInstruction())
                            processExpression(right)
                            val rightVar = flow.createTempVariable(parameterVar.dfType)
                            addImplicitConversion(right, parameterType)
                            addInstruction(JvmAssignmentInstruction(null, rightVar))
                            addInstruction(PopInstruction())
                            return rangeFunction(expr, parameterVar, leftVar, leftRelation, rightVar, rightRelation)
                        }
                    }
                }
            }
        }
        processExpression(range)
        if (range != null) {
            val kotlinType = range.getKotlinType()
            when (val lengthField = findSpecialField(kotlinType)) {
                SpecialField.ARRAY_LENGTH, SpecialField.STRING_LENGTH, SpecialField.COLLECTION_SIZE -> {
                    val collectionVar = flow.createTempVariable(kotlinType.toDfType())
                    addInstruction(JvmAssignmentInstruction(null, collectionVar))
                    addInstruction(PopInstruction())
                    return {
                        addInstruction(PushInstruction(lengthField.createValue(factory, collectionVar), null))
                        addInstruction(PushValueInstruction(DfTypes.intValue(0)))
                        addInstruction(BooleanBinaryInstruction(RelationType.GT, false, null))
                        pushUnknown()
                        addInstruction(BooleanAndOrInstruction(false, KotlinForVisitedAnchor(expr)))
                    }
                }
                SpecialField.UNBOX, SpecialField.OPTIONAL_VALUE, SpecialField.ENUM_ORDINAL, SpecialField.CONSUMED_STREAM,
                SpecialField.INSTANTIABLE_CLASS, null -> {}
            }
        }
        addInstruction(PopInstruction())
        return { pushUnknown() }
    }

    private fun rangeFunction(
        expr: KtForExpression,
        parameterVar: DfaVariableValue,
        leftVar: DfaValue,
        leftRelation: RelationType,
        rightVar: DfaValue,
        rightRelation: RelationType
    ): () -> Unit = {
        val forAnchor = KotlinForVisitedAnchor(expr)
        addInstruction(PushInstruction(parameterVar, null))
        addInstruction(PushInstruction(leftVar, null))
        addInstruction(BooleanBinaryInstruction(leftRelation, false, null))
        val offset = DeferredOffset()
        addInstruction(ConditionalGotoInstruction(offset, DfTypes.FALSE))
        addInstruction(PushInstruction(parameterVar, null))
        addInstruction(PushInstruction(rightVar, null))
        addInstruction(BooleanBinaryInstruction(rightRelation, false, forAnchor))
        val finalOffset = DeferredOffset()
        addInstruction(GotoInstruction(finalOffset))
        setOffset(offset)
        addInstruction(PushValueInstruction(DfTypes.FALSE, forAnchor))
        setOffset(finalOffset)
    }

    private fun processBlock(expr: KtBlockExpression) {
        val statements = expr.statements
        if (statements.isEmpty()) {
            pushUnknown()
        } else {
            for (child in statements) {
                processExpression(child)
                if (child != statements.last()) {
                    addInstruction(PopInstruction())
                }
                if (broken) return
            }
            addInstruction(FinishElementInstruction(expr))
        }
    }

    private fun processDeclaration(variable: KtProperty) {
        val initializer = variable.initializer
        if (initializer == null) {
            pushUnknown()
            return
        }
        val dfaVariable = factory.varFactory.createVariableValue(KtVariableDescriptor(variable))
        if (variable.isLocal && !variable.isVar && variable.type()?.isBoolean() == true) {
            // Boolean true/false constant: do not track; might be used as a feature knob or explanatory variable
            if (initializer.node?.elementType == KtNodeTypes.BOOLEAN_CONSTANT) {
                pushUnknown()
                return
            }
        }
        processExpression(initializer)
        addImplicitConversion(initializer, variable.type())
        addInstruction(JvmAssignmentInstruction(KotlinExpressionAnchor(variable), dfaVariable))
    }

    private fun processReturnExpression(expr: KtReturnExpression) {
        val returnedExpression = expr.returnedExpression
        processExpression(returnedExpression)
        if (expr.labeledExpression != null) {
            val targetFunction = expr.getTargetFunction(expr.safeAnalyzeNonSourceRootCode(BodyResolveMode.FULL))
            if (targetFunction != null && PsiTreeUtil.isAncestor(context, targetFunction, true)) {
                val transfer: InstructionTransfer
                if (returnedExpression != null) {
                    val retVar = flow.createTempVariable(returnedExpression.getKotlinType().toDfType())
                    addInstruction(JvmAssignmentInstruction(null, retVar))
                    transfer = createTransfer(targetFunction, targetFunction, retVar)
                } else {
                    transfer = createTransfer(targetFunction, targetFunction, factory.unknown)
                }
                addInstruction(ControlTransferInstruction(factory.controlTransfer(transfer, trapTracker.getTrapsInsideElement(targetFunction))))
                return
            }
        }
        addInstruction(ReturnInstruction(factory, trapTracker.trapStack(), expr))
    }

    private fun controlTransfer(target: TransferTarget, traps: FList<Trap>) {
        addInstruction(ControlTransferInstruction(factory.controlTransfer(target, traps)))
    }

    private fun createTransfer(exitedStatement: PsiElement, blockToFlush: PsiElement, resultValue: DfaValue,
                                exitBlock: Boolean = false): InstructionTransfer {
        val varsToFlush = PsiTreeUtil.findChildrenOfType(
            blockToFlush,
            KtProperty::class.java
        ).map { property -> KtVariableDescriptor(property) }
        return KotlinTransferTarget(resultValue, flow.getEndOffset(exitedStatement), exitBlock, varsToFlush)
    }

    private class KotlinTransferTarget(
        val resultValue: DfaValue,
        val offset: ControlFlowOffset,
        val exitBlock: Boolean,
        val varsToFlush: List<KtVariableDescriptor>
    ) : InstructionTransfer(offset, varsToFlush) {
        override fun dispatch(state: DfaMemoryState, interpreter: DataFlowInterpreter): MutableList<DfaInstructionState> {
            if (exitBlock) {
                val value = state.pop()
                check(!(value !is DfaControlTransferValue || value.target !== DfaControlTransferValue.RETURN_TRANSFER)) {
                    "Expected control transfer on stack; got $value"
                }
            }
            state.push(resultValue)
            return super.dispatch(state, interpreter)
        }

        override fun bindToFactory(factory: DfaValueFactory): TransferTarget {
            return KotlinTransferTarget(resultValue.bindToFactory(factory), offset, exitBlock, varsToFlush)
        }

        override fun toString(): String {
            return super.toString() + "; result = " + resultValue
        }
    }

    private fun processLabeledJumpExpression(expr: KtExpressionWithLabel) {
        val targetLoop = expr.targetLoop()
        if (targetLoop == null || !PsiTreeUtil.isAncestor(context, targetLoop, false)) {
            addInstruction(ControlTransferInstruction(trapTracker.transferValue(DfaControlTransferValue.RETURN_TRANSFER)))
        } else {
            val body = if (expr is KtBreakExpression) targetLoop else targetLoop.body!!
            val trapContainer = if (expr is KtBreakExpression) body else body.parent
            val transfer = createTransfer(body, body, factory.unknown, expr is KtBreakExpression)
            val transferValue = factory.controlTransfer(transfer, trapTracker.getTrapsInsideElement(trapContainer))
            addInstruction(ControlTransferInstruction(transferValue))
        }
    }

    private fun processThrowExpression(expr: KtThrowExpression) {
        val exception = expr.thrownExpression
        processExpression(exception)
        addInstruction(PopInstruction())
        if (exception != null) {
            val constraint = TypeConstraint.fromDfType(exception.getKotlinType().toDfType())
            if (constraint != TypeConstraints.TOP) {
                val kind = ExceptionTransfer(constraint)
                addInstruction(ThrowInstruction(trapTracker.transferValue(kind), expr))
                return
            }
        }
        pushUnknown()
    }

    private fun processReferenceExpression(expr: KtSimpleNameExpression, qualifierOnStack: Boolean = false) {
        val dfVar = KtVariableDescriptor.createFromSimpleName(factory, expr)
        if (dfVar != null) {
            if (qualifierOnStack) {
                addInstruction(PopInstruction())
            }
            addInstruction(PushInstruction(dfVar, KotlinExpressionAnchor(expr)))
            var realExpr: KtExpression = expr
            while (true) {
                val parent = realExpr.parent
                if (parent is KtQualifiedExpression && parent.selectorExpression == realExpr) {
                    realExpr = parent
                } else break
            }
            val exprType = realExpr.getKotlinType()
            val declaredType = when (val desc = dfVar.descriptor) {
                is KtVariableDescriptor -> desc.variable.type()
                is KtLambdaSpecialVariableDescriptor -> desc.type
                else -> null
            }
            addImplicitConversion(declaredType, exprType)
            return
        }
        var topExpr: KtExpression = expr
        while ((topExpr.parent as? KtQualifiedExpression)?.selectorExpression === topExpr) {
            topExpr = topExpr.parent as KtExpression
        }
        val target = expr.mainReference.resolve()
        val value: DfType? = getReferenceValue(topExpr, target)
        if (value != null) {
            if (qualifierOnStack) {
                addInstruction(PopInstruction())
            }
            addInstruction(PushValueInstruction(value, KotlinExpressionAnchor(expr)))
        } else {
            addCall(expr, 0, qualifierOnStack)
        }
    }

    private fun getReferenceValue(expr: KtExpression, target: PsiElement?): DfType? {
        return when (target) {
            // Companion object qualifier
            is KtObjectDeclaration -> DfType.TOP
            is PsiClass -> DfType.TOP
            is PsiVariable -> {
                val constantValue = target.computeConstantValue()
                val dfType = expr.getKotlinType().toDfType()
                if (constantValue != null && constantValue !is Boolean && dfType != DfType.TOP) {
                    DfTypes.constant(constantValue, dfType)
                } else {
                    dfType
                }
            }
            is KtEnumEntry -> {
                val ktClass = target.containingClass()
                val enumConstant = ktClass?.toLightClass()?.fields?.firstOrNull { f -> f is PsiEnumConstant && f.name == target.name }
                val dfType = expr.getKotlinType().toDfType()
                if (enumConstant != null && dfType is DfReferenceType) {
                    DfTypes.constant(enumConstant, dfType)
                } else {
                    DfType.TOP
                }
            }
            else -> null
        }
    }

    private fun processConstantExpression(expr: KtConstantExpression) {
        addInstruction(PushValueInstruction(getConstant(expr), KotlinExpressionAnchor(expr)))
    }

    private fun pushUnknown() {
        addInstruction(PushValueInstruction(DfType.TOP))
    }

    private fun processBinaryExpression(expr: KtBinaryExpression) {
        val token = expr.operationToken
        val relation = relationFromToken(token)
        if (relation != null) {
            processBinaryRelationExpression(expr, relation, token == KtTokens.EXCLEQ || token == KtTokens.EQEQ)
            return
        }
        val leftKtType = expr.left?.getKotlinType()
        if (token === KtTokens.PLUS && (KotlinBuiltIns.isString(leftKtType) || KotlinBuiltIns.isString(expr.right?.getKotlinType()))) {
            processExpression(expr.left)
            processExpression(expr.right)
            addInstruction(StringConcatInstruction(KotlinExpressionAnchor(expr), stringType))
            return
        }
        if (leftKtType?.toDfType() is DfIntegralType) {
            val mathOp = mathOpFromToken(expr.operationReference)
            if (mathOp != null) {
                processMathExpression(expr, mathOp)
                return
            }
        }
        if (token === KtTokens.ANDAND || token === KtTokens.OROR) {
            processShortCircuitExpression(expr, token === KtTokens.ANDAND)
            return
        }
        if (ASSIGNMENT_TOKENS.contains(token)) {
            processAssignmentExpression(expr)
            return
        }
        if (token === KtTokens.ELVIS) {
            processNullSafeOperator(expr)
            return
        }
        if (token === KtTokens.IN_KEYWORD) {
            val left = expr.left
            processExpression(left)
            processInCheck(left?.getKotlinType(), expr.right, KotlinExpressionAnchor(expr), false)
            return
        }
        if (token === KtTokens.NOT_IN) {
            val left = expr.left
            processExpression(left)
            processInCheck(left?.getKotlinType(), expr.right, KotlinExpressionAnchor(expr), true)
            return
        }
        processExpression(expr.left)
        processExpression(expr.right)
        addCall(expr, 2)
    }

    private fun processInCheck(kotlinType: KotlinType?, range: KtExpression?, anchor: KotlinAnchor, negated: Boolean) {
        if (kotlinType != null && (kotlinType.isInt() || kotlinType.isLong())) {
            if (range is KtBinaryExpression) {
                val type = range.getRangeBinaryExpressionType()
                val pair = when (type) {
                    RANGE_TO, UNTIL, RANGE_UNTIL -> range.left to range.right
                    null, DOWN_TO -> null
                }
                if (type != null && pair != null) {
                    val (left, right) = pair
                    val leftType = left?.getKotlinType()
                    val rightType = right?.getKotlinType()
                    if (leftType.toDfType() is DfIntegralType && rightType.toDfType() is DfIntegralType) {
                        processExpression(left)
                        addImplicitConversion(left, kotlinType)
                        processExpression(right)
                        addImplicitConversion(right, kotlinType)
                        addInstruction(SpliceInstruction(3, 2, 0, 2, 1))
                        addInstruction(BooleanBinaryInstruction(RelationType.GE, false, null))
                        val offset = DeferredOffset()
                        addInstruction(ConditionalGotoInstruction(offset, DfTypes.FALSE))
                        var relationType = type.getRelationType().second
                        if (negated) {
                            relationType = relationType.negated
                        }
                        addInstruction(BooleanBinaryInstruction(relationType, false, anchor))
                        val finalOffset = DeferredOffset()
                        addInstruction(GotoInstruction(finalOffset))
                        setOffset(offset)
                        addInstruction(SpliceInstruction(2))
                        addInstruction(PushValueInstruction(if (negated) DfTypes.TRUE else DfTypes.FALSE, anchor))
                        setOffset(finalOffset)
                        return
                    }
                }
            }
        }
        processExpression(range)
        addInstruction(EvalUnknownInstruction(anchor, 2, DfTypes.BOOLEAN))
        addInstruction(FlushFieldsInstruction())
    }

    private fun processNullSafeOperator(expr: KtBinaryExpression) {
        val left = expr.left
        processExpression(left)
        addInstruction(DupInstruction())
        val offset = DeferredOffset()
        addInstruction(ConditionalGotoInstruction(offset, DfTypes.NULL))
        val endOffset = DeferredOffset()
        addImplicitConversion(left?.getKotlinType(), expr.getKotlinType())
        addInstruction(GotoInstruction(endOffset))
        setOffset(offset)
        addInstruction(PopInstruction())
        processExpression(expr.right)
        setOffset(endOffset)
        addInstruction(ResultOfInstruction(KotlinExpressionAnchor(expr)))
    }

    private fun processAssignmentExpression(expr: KtBinaryExpression) {
        val left = expr.left
        val right = expr.right
        val token = expr.operationToken
        if (left is KtArrayAccessExpression && token == KtTokens.EQ) {
            // TODO: compound-assignment for arrays
            processArrayAccess(left, right)
            return
        }
        val dfVar = KtVariableDescriptor.createFromQualified(factory, left)
        val leftType = left?.getKotlinType()
        val rightType = right?.getKotlinType()
        if (dfVar == null) {
            processExpression(left)
            addInstruction(PopInstruction())
            processExpression(right)
            addImplicitConversion(right, leftType)
            // TODO: support safe-qualified assignments
            addInstruction(FlushFieldsInstruction())
            return
        }
        val mathOp = mathOpFromAssignmentToken(token)
        if (mathOp != null) {
            val resultType = balanceType(leftType, rightType)
            processExpression(left)
            addImplicitConversion(left, resultType)
            processExpression(right)
            addImplicitConversion(right, resultType)
            addInstruction(NumericBinaryInstruction(mathOp, KotlinExpressionAnchor(expr)))
            addImplicitConversion(resultType, leftType)
        } else {
            processExpression(right)
            addImplicitConversion(right, leftType)
        }
        // TODO: support overloaded assignment
        addInstruction(JvmAssignmentInstruction(KotlinExpressionAnchor(expr), dfVar))
        addInstruction(FinishElementInstruction(expr))
    }

    private fun processShortCircuitExpression(expr: KtBinaryExpression, and: Boolean) {
        val left = expr.left
        val right = expr.right
        val endOffset = DeferredOffset()
        processExpression(left)
        val targetType = expr.getKotlinType() // Boolean
        addImplicitConversion(left, targetType)
        val nextOffset = DeferredOffset()
        addInstruction(ConditionalGotoInstruction(nextOffset, DfTypes.booleanValue(and), left))
        val anchor = KotlinExpressionAnchor(expr)
        addInstruction(PushValueInstruction(DfTypes.booleanValue(!and), anchor))
        addInstruction(GotoInstruction(endOffset))
        setOffset(nextOffset)
        addInstruction(FinishElementInstruction(null))
        processExpression(right)
        addImplicitConversion(right, targetType)
        setOffset(endOffset)
        addInstruction(ResultOfInstruction(anchor))
    }

    private fun processMathExpression(expr: KtBinaryExpression, mathOp: LongRangeBinOp) {
        val left = expr.left
        val right = expr.right
        val resultType = expr.getKotlinType()
        processExpression(left)
        addImplicitConversion(left, resultType)
        processExpression(right)
        if (!mathOp.isShift) {
            addImplicitConversion(right, resultType)
        }
        if ((mathOp == LongRangeBinOp.DIV || mathOp == LongRangeBinOp.MOD) && resultType != null &&
            (resultType.isLong() || resultType.isInt())) {
            val transfer: DfaControlTransferValue? = trapTracker.maybeTransferValue("kotlin.ArithmeticException")
            val zero = if (resultType.isLong()) DfTypes.longValue(0) else DfTypes.intValue(0)
            addInstruction(EnsureInstruction(null, RelationType.NE, zero, transfer, true))
        }
        addInstruction(NumericBinaryInstruction(mathOp, KotlinExpressionAnchor(expr)))
    }

    private fun addImplicitConversion(expression: KtExpression?, expectedType: KotlinType?) {
        addImplicitConversion(expression?.getKotlinType(), expectedType)
    }

    private fun addImplicitConversion(actualType: KotlinType?, expectedType: KotlinType?) {
        actualType ?: return
        expectedType ?: return
        if (actualType == expectedType) return
        val actualDfType = actualType.toDfType()
        val expectedDfType = expectedType.toDfType()
        if (actualType.constructor.declarationDescriptor?.isInlineClass() == true &&
            expectedType.constructor.declarationDescriptor?.isInlineClass() != true) {
            addInstruction(PopInstruction())
            addInstruction(PushValueInstruction(actualDfType))
        }
        if (actualDfType !is DfPrimitiveType && expectedDfType is DfPrimitiveType) {
            addInstruction(GetQualifiedValueInstruction(SpecialField.UNBOX))
        }
        else if (expectedDfType !is DfPrimitiveType && actualDfType is DfPrimitiveType) {
            val dfType = actualType.makeNullable().toDfType().meet(DfTypes.NOT_NULL_OBJECT)
            addInstruction(WrapDerivedVariableInstruction(expectedType.toDfType().meet(dfType), SpecialField.UNBOX))
        }
        if (actualDfType is DfPrimitiveType && expectedDfType is DfPrimitiveType) {
            addInstruction(PrimitiveConversionInstruction(expectedType.toPsiPrimitiveType(), null))
        }
    }

    private fun processBinaryRelationExpression(
        expr: KtBinaryExpression, relation: RelationType,
        forceEqualityByContent: Boolean
    ) {
        val left = expr.left
        val right = expr.right
        val leftType = left?.getKotlinType()
        val rightType = right?.getKotlinType()
        processExpression(left)
        val leftDfType = leftType.toDfType()
        val rightDfType = rightType.toDfType()
        if ((relation == RelationType.EQ || relation == RelationType.NE) ||
            (leftDfType is DfPrimitiveType && rightDfType is DfPrimitiveType)) {
            val balancedType: KotlinType? = balanceType(leftType, rightType, forceEqualityByContent)
            val adjustedContentEquality = forceEqualityByContent && balancedType.toDfType() !is DfPrimitiveType
            addImplicitConversion(left, balancedType)
            processExpression(right)
            addImplicitConversion(right, balancedType)
            if (adjustedContentEquality && !mayCompareByContent(leftDfType, rightDfType)) {
                val transfer = trapTracker.maybeTransferValue("kotlin.Throwable")
                addInstruction(KotlinEqualityInstruction(expr, relation != RelationType.EQ, transfer))
            } else {
                addInstruction(BooleanBinaryInstruction(relation, adjustedContentEquality, KotlinExpressionAnchor(expr)))
            }
        } else {
            val leftConstraint = TypeConstraint.fromDfType(leftDfType)
            val rightConstraint = TypeConstraint.fromDfType(rightDfType)
            if (leftConstraint.isEnum && rightConstraint.isEnum && leftConstraint.meet(rightConstraint) != TypeConstraints.BOTTOM) {
                addInstruction(
                    GetQualifiedValueInstruction(SpecialField.ENUM_ORDINAL)
                )
                processExpression(right)
                addInstruction(
                    GetQualifiedValueInstruction(SpecialField.ENUM_ORDINAL)
                )
                addInstruction(BooleanBinaryInstruction(relation, forceEqualityByContent, KotlinExpressionAnchor(expr)))
            } else if (leftConstraint.isExact(CommonClassNames.JAVA_LANG_STRING) &&
                rightConstraint.isExact(CommonClassNames.JAVA_LANG_STRING)) {
                processExpression(right)
                addInstruction(BooleanBinaryInstruction(relation, forceEqualityByContent, KotlinExpressionAnchor(expr)))
            } else {
                // Overloaded >/>=/</<=: do not evaluate
                processExpression(right)
                addCall(expr, 2)
            }
        }
    }

    private fun mayCompareByContent(leftDfType: DfType, rightDfType: DfType): Boolean {
        if (leftDfType == DfTypes.NULL || rightDfType == DfTypes.NULL) return true
        if (leftDfType is DfPrimitiveType || rightDfType is DfPrimitiveType) return true
        val constraint = TypeConstraint.fromDfType(leftDfType)
        if (constraint.isComparedByEquals || constraint.isArray || constraint.isEnum) return true
        if (!constraint.isExact) return false
        val cls = PsiUtil.resolveClassInClassTypeOnly(constraint.getPsiType(factory.project)) ?: return false
        val equalsSignature =
            MethodSignatureUtil.createMethodSignature("equals", arrayOf(TypeUtils.getObjectType(context)), arrayOf(), PsiSubstitutor.EMPTY)
        val method = MethodSignatureUtil.findMethodBySignature(cls, equalsSignature, true)
        return method?.containingClass?.qualifiedName == CommonClassNames.JAVA_LANG_OBJECT
    }

    private fun balanceType(leftType: KotlinType?, rightType: KotlinType?, forceEqualityByContent: Boolean): KotlinType? = when {
        leftType == null || rightType == null -> null
        leftType.isNullableNothing() -> rightType.makeNullable()
        rightType.isNullableNothing() -> leftType.makeNullable()
        !forceEqualityByContent -> balanceType(leftType, rightType)
        leftType.isSubtypeOf(rightType) -> rightType
        rightType.isSubtypeOf(leftType) -> leftType
        else -> null
    }

    private fun balanceType(left: KotlinType?, right: KotlinType?): KotlinType? {
        if (left == null || right == null) return null
        if (left == right) return left
        if (left.canBeNull() && !right.canBeNull()) {
            return balanceType(left.makeNotNullable(), right)
        }
        if (!left.canBeNull() && right.canBeNull()) {
            return balanceType(left, right.makeNotNullable())
        }
        if (left.isDouble()) return left
        if (right.isDouble()) return right
        if (left.isFloat()) return left
        if (right.isFloat()) return right
        if (left.isLong()) return left
        if (right.isLong()) return right
        // The 'null' means no balancing is necessary
        return null
    }

    private fun addInstruction(inst: Instruction) {
        flow.addInstruction(inst)
    }

    private fun setOffset(offset: DeferredOffset) {
        offset.setOffset(flow.instructionCount)
    }

    private fun processWhenExpression(expr: KtWhenExpression) {
        val subjectExpression = expr.subjectExpression
        val dfVar: DfaVariableValue?
        val kotlinType: KotlinType?
        if (subjectExpression == null) {
            dfVar = null
            kotlinType = null
        } else {
            processExpression(subjectExpression)
            val subjectVariable = expr.subjectVariable
            if (subjectVariable != null) {
                kotlinType = subjectVariable.type()
                dfVar = factory.varFactory.createVariableValue(KtVariableDescriptor(subjectVariable))
            } else {
                kotlinType = subjectExpression.getKotlinType()
                dfVar = flow.createTempVariable(kotlinType.toDfType())
                addInstruction(JvmAssignmentInstruction(null, dfVar))
            }
            addInstruction(PopInstruction())
        }
        val endOffset = DeferredOffset()
        for (entry in expr.entries) {
            if (entry.isElse) {
                processExpression(entry.expression)
                addInstruction(GotoInstruction(endOffset))
            } else {
                val branchStart = DeferredOffset()
                for (condition in entry.conditions) {
                    processWhenCondition(dfVar, kotlinType, condition)
                    addInstruction(ConditionalGotoInstruction(branchStart, DfTypes.TRUE))
                }
                val skipBranch = DeferredOffset()
                addInstruction(GotoInstruction(skipBranch))
                setOffset(branchStart)
                processExpression(entry.expression)
                addInstruction(GotoInstruction(endOffset))
                setOffset(skipBranch)
            }
        }
        pushUnknown()
        setOffset(endOffset)
        addInstruction(FinishElementInstruction(expr))
    }

    private fun processWhenCondition(dfVar: DfaVariableValue?, dfVarType: KotlinType?, condition: KtWhenCondition) {
        when (condition) {
            is KtWhenConditionWithExpression -> {
                val expr = condition.expression
                processExpression(expr)
                val exprType = expr?.getKotlinType()
                if (dfVar != null) {
                    val balancedType = balanceType(exprType, dfVarType, true)
                    addImplicitConversion(exprType, balancedType)
                    addInstruction(PushInstruction(dfVar, null))
                    addImplicitConversion(dfVarType, balancedType)
                    addInstruction(BooleanBinaryInstruction(RelationType.EQ, true, KotlinWhenConditionAnchor(condition)))
                } else if (exprType?.canBeNull() == true) {
                    addInstruction(
                        GetQualifiedValueInstruction(SpecialField.UNBOX)
                    )
                }
            }
            is KtWhenConditionIsPattern -> {
                val type = getTypeCheckDfType(condition.typeReference)
                if (dfVar == null || type == DfType.TOP) {
                    pushUnknown()
                } else {
                    addInstruction(PushInstruction(dfVar, null))
                    addInstruction(PushValueInstruction(type))
                    if (condition.isNegated) {
                        addInstruction(InstanceofInstruction(null, false))
                        addInstruction(NotInstruction(KotlinWhenConditionAnchor(condition)))
                    } else {
                        addInstruction(InstanceofInstruction(KotlinWhenConditionAnchor(condition), false))
                    }
                }
            }
            is KtWhenConditionInRange -> {
                if (dfVar != null) {
                    addInstruction(PushInstruction(dfVar, null))
                } else {
                    pushUnknown()
                }
                processInCheck(dfVarType, condition.rangeExpression, KotlinWhenConditionAnchor(condition), condition.isNegated)
            }
            else -> broken = true
        }
    }

    private fun getTypeCheckDfType(typeReference: KtTypeReference?): DfType {
        if (typeReference == null) return DfType.TOP
        val kotlinType = typeReference.getAbbreviatedTypeOrType(typeReference.safeAnalyzeNonSourceRootCode(BodyResolveMode.FULL))
        if (kotlinType == null || kotlinType.constructor.declarationDescriptor is TypeParameterDescriptor) return DfType.TOP
        val result = if (kotlinType.isMarkedNullable) kotlinType.toDfType()
        else {
            // makeNullable to convert primitive to boxed
            val dfType = kotlinType.makeNullable().toDfType().meet(DfTypes.NOT_NULL_OBJECT)
            if (dfType is DfReferenceType) dfType.dropSpecialField() else dfType
        }
        return if (result is DfReferenceType)
            // Convert Java to Kotlin types if necessary
            result.convert(KtClassDef.typeConstraintFactory(typeReference))
        else
            result
    }

    private fun processIfExpression(ifExpression: KtIfExpression) {
        val condition = ifExpression.condition
        processExpression(condition)
        if (condition?.getKotlinType()?.canBeNull() == true) {
            addInstruction(GetQualifiedValueInstruction(SpecialField.UNBOX))
        }
        val skipThenOffset = DeferredOffset()
        val thenStatement = ifExpression.then
        val elseStatement = ifExpression.`else`
        val exprType = ifExpression.getKotlinType()
        addInstruction(ConditionalGotoInstruction(skipThenOffset, DfTypes.FALSE, condition))
        addInstruction(FinishElementInstruction(null))
        processExpression(thenStatement)
        addImplicitConversion(thenStatement, exprType)

        val skipElseOffset = DeferredOffset()
        addInstruction(GotoInstruction(skipElseOffset))
        setOffset(skipThenOffset)
        addInstruction(FinishElementInstruction(null))
        processExpression(elseStatement)
        addImplicitConversion(elseStatement, exprType)
        setOffset(skipElseOffset)
        addInstruction(FinishElementInstruction(ifExpression))
    }

    companion object {
        private val LOG = logger<KtControlFlowBuilder>()
        private val ASSIGNMENT_TOKENS = TokenSet.create(KtTokens.EQ, KtTokens.PLUSEQ, KtTokens.MINUSEQ, KtTokens.MULTEQ, KtTokens.DIVEQ, KtTokens.PERCEQ)
        private val unsupported = ConcurrentHashMap.newKeySet<String>()
    }
}
