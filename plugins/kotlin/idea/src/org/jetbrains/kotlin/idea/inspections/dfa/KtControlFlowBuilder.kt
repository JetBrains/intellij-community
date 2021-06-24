// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInsight.Nullability
import com.intellij.codeInspection.dataFlow.TypeConstraints
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.java.inst.*
import com.intellij.codeInspection.dataFlow.jvm.SpecialField
import com.intellij.codeInspection.dataFlow.jvm.transfer.ExceptionTransfer
import com.intellij.codeInspection.dataFlow.jvm.transfer.InstructionTransfer
import com.intellij.codeInspection.dataFlow.lang.ir.*
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet
import com.intellij.codeInspection.dataFlow.types.*
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue.Trap
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.codeInspection.dataFlow.value.RelationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.*
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.FList
import com.intellij.util.containers.FactoryMap
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinAnchor.KotlinExpressionAnchor
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinAnchor.KotlinWhenConditionAnchor
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinProblem.KotlinArrayIndexProblem
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinProblem.KotlinCastProblem
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.targetLoop
import org.jetbrains.kotlin.idea.project.builtIns
import org.jetbrains.kotlin.idea.refactoring.move.moveMethod.type
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class KtControlFlowBuilder(val factory: DfaValueFactory, val context: KtExpression) {
    // Will be used to track catch/finally blocks
    private val traps: FList<Trap> = FList.emptyList()
    private val flow = ControlFlow(factory, context)
    private var broken: Boolean = false
    private val exceptionCache = FactoryMap.create<String, ExceptionTransfer>
    { fqn -> ExceptionTransfer(TypeConstraints.instanceOf(createClassType(fqn))) }
    private val stringType = createClassType(CommonClassNames.JAVA_LANG_STRING)

    private fun createClassType(fqn: String): PsiClassType {
        val project = factory.project
        val scope = context.resolveScope
        val aClass = JavaPsiFacade.getInstance(project).findClass(fqn, scope)
        val elementFactory = JavaPsiFacade.getElementFactory(project)
        return if (aClass != null) elementFactory.createType(aClass) else elementFactory.createTypeByFQClassName(fqn, scope)
    }

    fun buildFlow(): ControlFlow? {
        processExpression(context)
        if (LOG.isDebugEnabled) {
            val total = totalCount.incrementAndGet()
            val success = if (!broken) successCount.incrementAndGet() else successCount.get()
            if (total % 100 == 0) {
                LOG.info("Analyzed: "+success+" of "+total + " ("+success*100/total + "%)")
            }
        }
        if (broken) return null
        addInstruction(PopInstruction()) // return value
        flow.finish()
        return flow
    }

    private fun processExpression(expr: KtExpression?) {
        when (expr) {
            null -> pushUnknown()
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
            // KtSuperExpression, KtTryExpression
            // KtCallableReferenceExpression, KtObjectLiteralExpression
            // KtDestructuringDeclaration, KtNamedFunction, KtClass
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
        flow.finishElement(expr)
    }

    private fun processThisExpression(expr: KtThisExpression) {
        val dfType = expr.getKotlinType().toDfType(expr)
        val descriptor = expr.analyze(BodyResolveMode.FULL)[BindingContext.REFERENCE_TARGET, expr.instanceReference]
        if (descriptor != null) {
            val varDesc = KtThisDescriptor(descriptor, dfType)
            addInstruction(PushInstruction(factory.varFactory.createVariableValue(varDesc), KotlinExpressionAnchor(expr)))
        } else {
            addInstruction(PushValueInstruction(dfType, KotlinExpressionAnchor(expr)))
        }
    }

    private fun processClassLiteralExpression(expr: KtClassLiteralExpression) {
        val kotlinType = expr.getKotlinType()
        if (kotlinType != null) {
            val arguments = kotlinType.arguments
            if (arguments.size == 1) {
                val kType = arguments[0].type
                val kClassPsiType = kotlinType.toPsiType(expr)
                if (kClassPsiType != null) {
                    val kClassConstant: DfType = DfTypes.referenceConstant(kType, kClassPsiType)
                    addInstruction(PushValueInstruction(kClassConstant, KotlinExpressionAnchor(expr)))
                    return
                }
            }
        }
        addInstruction(PushValueInstruction(kotlinType.toDfType(expr)))
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
        val operandType = operand.getKotlinType()
        if (operandType.toDfType(expr) is DfPrimitiveType) {
            addInstruction(WrapDerivedVariableInstruction(DfTypes.NOT_NULL_OBJECT, SpecialField.UNBOX))
        }
        if (ref.text == "as?") {
            val tempVariable: DfaVariableValue = flow.createTempVariable(DfTypes.OBJECT_OR_NULL)
            addInstruction(SimpleAssignmentInstruction(null, tempVariable))
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
            val transfer = createTransfer("java.lang.ClassCastException")
            addInstruction(EnsureInstruction(KotlinCastProblem(operand, expr), RelationType.IS, type, transfer))
            if (typeReference != null) {
                val castType = typeReference.getAbbreviatedTypeOrType(typeReference.analyze(BodyResolveMode.FULL))
                if (castType.toDfType(typeReference) is DfPrimitiveType) {
                    addInstruction(UnwrapDerivedVariableInstruction(SpecialField.UNBOX))
                }
            }
        }
    }

    private fun processArrayAccess(expr: KtArrayAccessExpression) {
        val arrayExpression = expr.arrayExpression
        processExpression(arrayExpression)
        val kotlinType = arrayExpression?.getKotlinType()
        var curType = kotlinType
        val indexes = expr.indexExpressions
        for (idx in indexes) {
            processExpression(idx)
            val anchor = if (idx == indexes.last()) KotlinExpressionAnchor(expr) else null
            if (curType != null && KotlinBuiltIns.isArrayOrPrimitiveArray(curType)) {
                if (idx.getKotlinType()?.canBeNull() == true) {
                    addInstruction(UnwrapDerivedVariableInstruction(SpecialField.UNBOX))
                }
                val transfer = createTransfer("java.lang.ArrayIndexOutOfBoundsException")
                addInstruction(ArrayAccessInstruction(transfer, anchor, KotlinArrayIndexProblem(expr, idx), null))
                curType = expr.builtIns.getArrayElementType(curType)
            } else {
                // TODO: support string index (charAt)
                addInstruction(EvalUnknownInstruction(anchor, 2))
                addInstruction(FlushFieldsInstruction())
            }
        }
    }

    private fun processIsExpression(expr: KtIsExpression) {
        processExpression(expr.leftHandSide)
        val type = getTypeCheckDfType(expr.typeReference)
        if (type == DfType.TOP) {
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
            addInstruction(PushValueInstruction(DfTypes.constant("", stringType)))
            return
        }
        val lastEntry = entries.last()
        for (entry in entries) {
            when (entry) {
                is KtEscapeStringTemplateEntry ->
                    addInstruction(PushValueInstruction(DfTypes.constant(entry.unescapedValue, stringType)))
                is KtLiteralStringTemplateEntry ->
                    addInstruction(PushValueInstruction(DfTypes.constant(entry.text, stringType)))
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
    }

    private fun processLambda(expr: KtLambdaExpression) {
        val element = expr.bodyExpression
        if (element != null) {
            addInstruction(ClosureInstruction(listOf(element)))
        }
        pushUnknown()
    }

    private fun processCallExpression(expr: KtCallExpression) {
        // TODO: recognize constructors, set the exact type for the result
        val args = expr.valueArgumentList?.arguments
        if (args != null) {
            for (arg: KtValueArgument in args) {
                val argExpr = arg.getArgumentExpression()
                if (argExpr != null) {
                    processExpression(argExpr)
                    addInstruction(PopInstruction())
                }
            }
        }
        for(lambdaArg in expr.lambdaArguments) {
            processExpression(lambdaArg.getLambdaExpression())
            addInstruction(PopInstruction())
        }
        pushUnknown()
        addInstruction(FlushFieldsInstruction())
        // TODO: support pure calls, some known methods, probably Java contracts, etc.
    }

    private fun processQualifiedReferenceExpression(expr: KtQualifiedExpression) {
        // TODO: support qualified references as variables
        val receiver = expr.receiverExpression
        val selector = expr.selectorExpression
        if (pushJavaClassConstant(receiver, selector, expr)) return
        processExpression(receiver)
        val specialField = if (expr is KtDotQualifiedExpression) findSpecialField(expr) else null
        if (specialField != null) {
            addInstruction(UnwrapDerivedVariableInstruction(specialField))
        } else {
            addInstruction(PopInstruction())
            processExpression(selector)
            addInstruction(PopInstruction())
            pushUnknown()
        }
    }

    private fun pushJavaClassConstant(receiver: KtExpression, selector: KtExpression?, expr: KtQualifiedExpression): Boolean {
        // TODO: a special instruction that converts KClass constant to Class constant
        if (receiver !is KtClassLiteralExpression || selector == null || !selector.textMatches("java")) return false
        val kotlinType = expr.getKotlinType() ?: return false
        val arguments = kotlinType.arguments
        if (arguments.size != 1) return false
        val psiType = arguments[0].type.toPsiType(expr) ?: return false
        val classPsiType = kotlinType.toPsiType(expr) ?: return false
        val classConstant: DfType = DfTypes.referenceConstant(psiType, classPsiType)
        addInstruction(PushValueInstruction(classConstant, KotlinExpressionAnchor(expr)))
        return true
    }

    private fun findSpecialField(expr: KtQualifiedExpression): SpecialField? {
        val selector = expr.selectorExpression ?: return null
        val receiver = expr.receiverExpression
        when (selector.text) {
            "size" -> {
                val type = receiver.getKotlinType() ?: return null
                when {
                    KotlinBuiltIns.isArray(type) || KotlinBuiltIns.isPrimitiveArray(type) -> {
                        return SpecialField.ARRAY_LENGTH
                    }
                    KotlinBuiltIns.isCollectionOrNullableCollection(type) || KotlinBuiltIns.isMapOrNullableMap(type) -> {
                        return SpecialField.COLLECTION_SIZE
                    }
                    else -> return null
                }
            }
            "length" -> {
                val type = receiver.getKotlinType() ?: return null
                return when {
                    KotlinBuiltIns.isStringOrNullableString(type) -> SpecialField.STRING_LENGTH
                    else -> null
                }
            }
            else -> return null
        }
    }

    private fun processPrefixExpression(expr: KtPrefixExpression) {
        val operand = expr.baseExpression
        processExpression(operand)
        val anchor = KotlinExpressionAnchor(expr)
        if (operand != null) {
            val dfType = operand.getKotlinType().toDfType(expr)
            val descriptor = KtLocalVariableDescriptor.create(operand)
            val ref = expr.operationReference.text
            if (dfType is DfIntegralType) {
                when (ref) {
                    "++", "--" -> {
                        if (descriptor != null) {
                            addInstruction(PushValueInstruction(dfType.meetRange(LongRangeSet.point(1))))
                            addInstruction(NumericBinaryInstruction(if (ref == "++") LongRangeBinOp.PLUS else LongRangeBinOp.MINUS, null))
                            addInstruction(SimpleAssignmentInstruction(anchor, factory.varFactory.createVariableValue(descriptor)))
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
            if (descriptor != null && (ref == "++" || ref == "--")) {
                // Custom inc/dec may update the variable
                addInstruction(FlushVariableInstruction(factory.varFactory.createVariableValue(descriptor)))
            }
        }
        addInstruction(EvalUnknownInstruction(anchor, 1))
    }

    private fun processPostfixExpression(expr: KtPostfixExpression) {
        val operand = expr.baseExpression
        processExpression(operand)
        val anchor = KotlinExpressionAnchor(expr)
        val ref = expr.operationReference.text
        if (ref == "++" || ref == "--") {
            if (operand != null) {
                val dfType = operand.getKotlinType().toDfType(expr)
                val descriptor = KtLocalVariableDescriptor.create(operand)
                if (descriptor != null) {
                    if (dfType is DfIntegralType) {
                        addInstruction(DupInstruction())
                        addInstruction(PushValueInstruction(dfType.meetRange(LongRangeSet.point(1))))
                        addInstruction(NumericBinaryInstruction(if (ref == "++") LongRangeBinOp.PLUS else LongRangeBinOp.MINUS, null))
                        addInstruction(SimpleAssignmentInstruction(anchor, factory.varFactory.createVariableValue(descriptor)))
                        addInstruction(PopInstruction())
                    } else {
                        // Custom inc/dec may update the variable
                        addInstruction(FlushVariableInstruction(factory.varFactory.createVariableValue(descriptor)))
                    }
                }
            }
        } else if (ref == "!!") {
            val transfer: DfaControlTransferValue? = createTransfer("java.lang.NullPointerException")
            addInstruction(EnsureInstruction(null, RelationType.NE, DfTypes.NULL, transfer))
            // Probably unbox
            addImplicitConversion(expr, operand?.getKotlinType(), expr.getKotlinType())
        } else {
            addInstruction(EvalUnknownInstruction(anchor, 1))
        }
    }

    private fun processDoWhileExpression(expr: KtDoWhileExpression) {
        val offset = ControlFlow.FixedOffset(flow.instructionCount)
        processExpression(expr.body)
        addInstruction(PopInstruction())
        processExpression(expr.condition)
        addInstruction(ConditionalGotoInstruction(offset, DfTypes.TRUE))
        flow.finishElement(expr)
        pushUnknown()
        addInstruction(FinishElementInstruction(expr))
    }

    private fun processWhileExpression(expr: KtWhileExpression) {
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
        pushUnknown()
        addInstruction(FinishElementInstruction(expr))
    }

    private fun processForExpression(expr: KtForExpression) {
        val range = expr.loopRange
        processExpression(range)
        addInstruction(PopInstruction())
        // TODO: process collections and integer ranges in a special way
        val startOffset = ControlFlow.FixedOffset(flow.instructionCount)
        val endOffset = DeferredOffset()
        pushUnknown()
        addInstruction(ConditionalGotoInstruction(endOffset, DfTypes.FALSE))
        val parameter = expr.loopParameter
        if (parameter == null) {
            // TODO: support destructuring declarations
            broken = true
            return
        }
        val descriptor = KtLocalVariableDescriptor(parameter)
        addInstruction(FlushVariableInstruction(factory.varFactory.createVariableValue(descriptor)))
        processExpression(expr.body)
        addInstruction(PopInstruction())
        addInstruction(GotoInstruction(startOffset))
        setOffset(endOffset)
        flow.finishElement(expr)
        pushUnknown()
        addInstruction(FinishElementInstruction(expr))
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
        val dfaVariable = factory.varFactory.createVariableValue(KtLocalVariableDescriptor(variable))
        processExpression(initializer)
        addImplicitConversion(initializer, variable.type())
        addInstruction(SimpleAssignmentInstruction(KotlinExpressionAnchor(variable), dfaVariable))
    }

    private fun processReturnExpression(expr: KtReturnExpression) {
        if (expr.labeledExpression != null) {
            // TODO: support labels
            broken = true
            return
        }
        processExpression(expr.returnedExpression)
        addInstruction(ReturnInstruction(factory, traps, expr))
        pushUnknown()
    }

    private fun getTrapsInsideElement(element: PsiElement): FList<Trap> {
        return FList.createFromReversed(traps.filter { trap -> PsiTreeUtil.isAncestor(element, trap.anchor, true) }.asReversed())
    }

    private fun createTransfer(exitedStatement: PsiElement, blockToFlush: PsiElement): InstructionTransfer {
        val varsToFlush = PsiTreeUtil.findChildrenOfType(
            blockToFlush,
            KtProperty::class.java
        ).map { property -> KtLocalVariableDescriptor(property) }
        return object : InstructionTransfer(flow.getEndOffset(exitedStatement), varsToFlush) {
            override fun dispatch(state: DfaMemoryState, interpreter: DataFlowInterpreter): MutableList<DfaInstructionState> {
                state.push(factory.unknown)
                return super.dispatch(state, interpreter)
            }
        }
    }

    private fun processLabeledJumpExpression(expr: KtExpressionWithLabel) {
        val targetLoop = expr.targetLoop()
        if (targetLoop == null || !PsiTreeUtil.isAncestor(context, targetLoop, false)) {
            addInstruction(ControlTransferInstruction(factory.controlTransfer(DfaControlTransferValue.RETURN_TRANSFER, traps)))
        } else {
            val body = if (expr is KtBreakExpression) targetLoop else targetLoop.body!!
            addInstruction(ControlTransferInstruction(factory.controlTransfer(createTransfer(body, body), getTrapsInsideElement(body))))
        }
    }

    private fun processThrowExpression(expr: KtThrowExpression) {
        val exception = expr.thrownExpression
        processExpression(exception)
        addInstruction(PopInstruction())
        if (exception != null) {
            val psiType = exception.getKotlinType()?.toPsiType(expr)
            if (psiType != null) {
                val kind = ExceptionTransfer(TypeConstraints.instanceOf(psiType))
                addInstruction(ThrowInstruction(factory.controlTransfer(kind, traps), expr))
                return
            }
        }
        pushUnknown()
    }

    private fun processReferenceExpression(expr: KtSimpleNameExpression) {
        val descriptor = KtLocalVariableDescriptor.create(expr)
        if (descriptor != null) {
            addInstruction(JvmPushInstruction(descriptor.createValue(factory, null), KotlinExpressionAnchor(expr)))
            val exprType = expr.getKotlinType()
            val declaredType = descriptor.variable.type()
            addImplicitConversion(expr, declaredType, exprType)
            return
        }
        addInstruction(FlushFieldsInstruction())
        pushUnknown()
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
        if (leftKtType?.toDfType(expr) is DfIntegralType) {
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
        // TODO: support other operators
        processExpression(expr.left)
        processExpression(expr.right)
        addInstruction(EvalUnknownInstruction(KotlinExpressionAnchor(expr), 2))
        addInstruction(FlushFieldsInstruction())
    }

    private fun processInCheck(kotlinType: KotlinType?, range: KtExpression?, anchor: KotlinAnchor, negated: Boolean) {
        if (kotlinType != null && (kotlinType.isInt() || kotlinType.isLong())) {
            if (range is KtBinaryExpression) {
                val ref = range.operationReference.text
                if (ref == ".." || ref == "until") {
                    val left = range.left
                    val right = range.right
                    val leftType = left?.getKotlinType()
                    val rightType = right?.getKotlinType()
                    if (leftType.toDfType(range) is DfIntegralType && rightType.toDfType(range) is DfIntegralType) {
                        processExpression(left)
                        addImplicitConversion(left, kotlinType)
                        processExpression(right)
                        addImplicitConversion(right, kotlinType)
                        addInstruction(SpliceInstruction(3, 2, 0, 2, 1))
                        addInstruction(BooleanBinaryInstruction(RelationType.GE, false, null))
                        val offset = DeferredOffset()
                        addInstruction(ConditionalGotoInstruction(offset, DfTypes.FALSE))
                        var relationType = if (ref == "until") RelationType.LT else RelationType.LE
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
        addInstruction(EvalUnknownInstruction(anchor, 2))
        addInstruction(FlushFieldsInstruction())
    }

    private fun processNullSafeOperator(expr: KtBinaryExpression) {
        processExpression(expr.left)
        addInstruction(DupInstruction())
        val offset = DeferredOffset()
        addInstruction(ConditionalGotoInstruction(offset, DfTypes.NULL))
        val endOffset = DeferredOffset()
        addInstruction(GotoInstruction(endOffset))
        setOffset(offset)
        addInstruction(PopInstruction())
        processExpression(expr.right)
        setOffset(endOffset)
    }

    private fun processAssignmentExpression(expr: KtBinaryExpression) {
        val left = expr.left
        val right = expr.right
        val descriptor = KtLocalVariableDescriptor.create(left)
        val leftType = left?.getKotlinType()
        val rightType = right?.getKotlinType()
        if (descriptor == null) {
            processExpression(left)
            addInstruction(PopInstruction())
            processExpression(right)
            addImplicitConversion(right, leftType)
            // TODO: support qualified assignments
            addInstruction(FlushFieldsInstruction())
            return
        }
        val token = expr.operationToken
        val mathOp = mathOpFromAssignmentToken(token)
        if (mathOp != null) {
            val resultType = balanceType(leftType, rightType)
            processExpression(left)
            addImplicitConversion(left, resultType)
            processExpression(right)
            addImplicitConversion(right, resultType)
            addInstruction(NumericBinaryInstruction(mathOp, KotlinExpressionAnchor(expr)))
            addImplicitConversion(right, resultType, leftType)
        } else {
            processExpression(right)
            addImplicitConversion(right, leftType)
        }
        // TODO: support overloaded assignment
        addInstruction(SimpleAssignmentInstruction(KotlinExpressionAnchor(expr), factory.varFactory.createVariableValue(descriptor)))
        addInstruction(FinishElementInstruction(expr))
    }

    private fun processShortCircuitExpression(expr: KtBinaryExpression, and: Boolean) {
        val left = expr.left
        val right = expr.right
        val endOffset = DeferredOffset()
        processExpression(left)
        val nextOffset = DeferredOffset()
        addInstruction(ConditionalGotoInstruction(nextOffset, DfTypes.booleanValue(and), left))
        val anchor = KotlinExpressionAnchor(expr)
        addInstruction(PushValueInstruction(DfTypes.booleanValue(!and), anchor))
        addInstruction(GotoInstruction(endOffset))
        setOffset(nextOffset)
        addInstruction(FinishElementInstruction(null))
        processExpression(right)
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
        if (mathOp == LongRangeBinOp.DIV || mathOp == LongRangeBinOp.MOD) {
            val transfer: DfaControlTransferValue? = createTransfer("java.lang.ArithmeticException")
            val zero = if (resultType?.isLong() == true) DfTypes.longValue(0) else DfTypes.intValue(0)
            addInstruction(EnsureInstruction(null, RelationType.NE, zero, transfer, true))
        }
        if (!mathOp.isShift) {
            addImplicitConversion(right, resultType)
        }
        addInstruction(NumericBinaryInstruction(mathOp, KotlinExpressionAnchor(expr)))
    }

    fun createTransfer(exception: String): DfaControlTransferValue? {
        return if (!traps.isEmpty()) factory.controlTransfer(exceptionCache[exception], traps) else null
    }


    private fun addImplicitConversion(expression: KtExpression?, expectedType: KotlinType?) {
        addImplicitConversion(expression, expression?.getKotlinType(), expectedType)
    }

    private fun addImplicitConversion(expression: KtExpression?, actualType: KotlinType?, expectedType: KotlinType?) {
        expression ?: return
        actualType ?: return
        expectedType ?: return
        if (actualType == expectedType) return
        val actualPsiType = actualType.toPsiType(expression)
        val expectedPsiType = expectedType.toPsiType(expression)
        if (actualPsiType !is PsiPrimitiveType && expectedPsiType is PsiPrimitiveType) {
            addInstruction(UnwrapDerivedVariableInstruction(SpecialField.UNBOX))
        }
        else if (expectedPsiType !is PsiPrimitiveType && actualPsiType is PsiPrimitiveType) {
            val boxedType = actualPsiType.getBoxedType(expression)
            val dfType = if (boxedType != null) DfTypes.typedObject(boxedType, Nullability.NOT_NULL) else DfTypes.NOT_NULL_OBJECT
            addInstruction(WrapDerivedVariableInstruction(expectedType.toDfType(expression).meet(dfType), SpecialField.UNBOX))
        }
        if (actualPsiType is PsiPrimitiveType && expectedPsiType is PsiPrimitiveType) {
            addInstruction(PrimitiveConversionInstruction(expectedPsiType, null))
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
        if ((relation == RelationType.EQ || relation == RelationType.NE) ||
            (leftType.toDfType(expr) is DfPrimitiveType && rightType.toDfType(expr) is DfPrimitiveType)) {
            val balancedType: KotlinType? = balanceType(leftType, rightType, forceEqualityByContent)
            addImplicitConversion(left, balancedType)
            processExpression(right)
            addImplicitConversion(right, balancedType)
            // TODO: avoid equals-comparison of unknown object types
            addInstruction(BooleanBinaryInstruction(relation, forceEqualityByContent, KotlinExpressionAnchor(expr)))
        } else {
            // Overloaded >/>=/</<=: do not evaluate
            processExpression(right)
            addInstruction(EvalUnknownInstruction(KotlinExpressionAnchor(expr), 2))
            addInstruction(FlushFieldsInstruction())
        }
    }

    private fun balanceType(leftType: KotlinType?, rightType: KotlinType?, forceEqualityByContent: Boolean): KotlinType? = when {
        leftType == null || rightType == null -> null
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
                dfVar = factory.varFactory.createVariableValue(KtLocalVariableDescriptor(subjectVariable))
            } else {
                kotlinType = subjectExpression.getKotlinType()
                dfVar = flow.createTempVariable(kotlinType.toDfType(expr))
            }
            addInstruction(SimpleAssignmentInstruction(null, dfVar))
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
                    addImplicitConversion(expr, exprType, balancedType)
                    addInstruction(PushInstruction(dfVar, null))
                    addImplicitConversion(null, dfVarType, balancedType)
                    addInstruction(BooleanBinaryInstruction(RelationType.EQ, true, KotlinWhenConditionAnchor(condition)))
                } else if (exprType?.canBeNull() == true) {
                    addInstruction(UnwrapDerivedVariableInstruction(SpecialField.UNBOX))
                }
            }
            is KtWhenConditionIsPattern -> {
                if (dfVar != null) {
                    addInstruction(PushInstruction(dfVar, null))
                    val type = getTypeCheckDfType(condition.typeReference)
                    if (type == DfType.TOP) {
                        pushUnknown()
                    } else {
                        addInstruction(PushValueInstruction(type))
                        if (condition.isNegated) {
                            addInstruction(InstanceofInstruction(null, false))
                            addInstruction(NotInstruction(KotlinWhenConditionAnchor(condition)))
                        } else {
                            addInstruction(InstanceofInstruction(KotlinWhenConditionAnchor(condition), false))
                        }
                    }
                } else {
                    pushUnknown()
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
        val kotlinType = typeReference.getAbbreviatedTypeOrType(typeReference.analyze(BodyResolveMode.FULL))
        val type = kotlinType.toDfType(typeReference)
        return if (type is DfPrimitiveType) {
            val boxedType = (kotlinType?.toPsiType(typeReference) as? PsiPrimitiveType)?.getBoxedType(typeReference)
            if (boxedType != null) {
                DfTypes.typedObject(boxedType, Nullability.NOT_NULL)
            } else {
                DfType.TOP
            }
        } else type
    }

    private fun processIfExpression(ifExpression: KtIfExpression) {
        val condition = ifExpression.condition
        processExpression(condition)
        if (condition?.getKotlinType()?.canBeNull() == true) {
            addInstruction(UnwrapDerivedVariableInstruction(SpecialField.UNBOX))
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
        private val totalCount = AtomicInteger()
        private val successCount = AtomicInteger()
        private val unsupported = ConcurrentHashMap.newKeySet<String>()
    }
}