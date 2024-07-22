// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa

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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.*
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.*
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.FList
import com.siyeh.ig.psiutils.TypeUtils
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.contracts.description.KaContractCallsInPlaceContractEffectDeclaration
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.contracts.description.EventOccurrencesRange
import org.jetbrains.kotlin.idea.codeinsight.utils.isEnum
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinAnchor
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinAnchor.*
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinCallableReferenceInstruction
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinEqualityInstruction
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinProblem.*
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa.KtClassDef.Companion.classDef
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa.KtVariableDescriptor.Companion.variableDescriptor
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import java.util.concurrent.ConcurrentHashMap

class KtControlFlowBuilder(val factory: DfaValueFactory, val context: KtExpression) {
    private val flow = ControlFlow(factory, context)
    private val constraintFactory = KtClassDef.typeConstraintFactory(context)
    private val trapTracker = TrapTracker(factory, constraintFactory)
    private val stringType = constraintFactory.create(StandardNames.FqNames.string.asString())
    private var broken: Boolean = false

    fun buildFlow(): ControlFlow? {
        analyze(context) { processExpression(context) }
        if (broken) return null
        addInstruction(PopInstruction()) // return value
        flow.finish()
        return flow
    }

    context(KaSession)
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
                    if (LOG.isDebugEnabled || ApplicationManager.getApplication().isUnitTestMode) {
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

    private fun addInstruction(inst: Instruction) {
        flow.addInstruction(inst)
    }

    private fun setOffset(offset: DeferredOffset) {
        offset.setOffset(flow.instructionCount)
    }

    private fun pushUnknown() {
        addInstruction(PushValueInstruction(DfType.TOP))
    }

    context(KaSession)
    private fun processConstant(expr: KtExpression?): Boolean {
        val constantValue = expr?.evaluate() ?: return false
        if (constantValue is KaConstantValue.ErrorValue) return false
        val value = constantValue.value
        val ktType = when(value) {
            is Boolean -> builtinTypes.boolean
            is Int -> builtinTypes.int
            is Long -> builtinTypes.long
            is Float -> builtinTypes.float
            is Double -> builtinTypes.double
            is String -> builtinTypes.string
            // Unsigned type handling is not supported yet
            else -> return false
        }
        val dfType = ktType.toDfType()
        if (dfType == DfType.TOP) {
            // Likely, no STDLIB
            return false
        }
        addInstruction(PushValueInstruction(DfTypes.constant(value, dfType), KotlinExpressionAnchor(expr)))
        addImplicitConversion(ktType, expr.getKotlinType())
        return true
    }

    context(KaSession)
    private fun processConstantExpression(expr: KtConstantExpression) {
        addInstruction(PushValueInstruction(getConstant(expr), KotlinExpressionAnchor(expr)))
    }

    context(KaSession)
    private fun processLambda(expr: KtLambdaExpression) {
        val element = expr.bodyExpression
        if (element != null) {
            processEscapes(element)
            addInstruction(ClosureInstruction(listOf(element)))
        }
        pushUnknown()
    }

    context(KaSession)
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

    context(KaSession)
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

    context(KaSession)
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

    context(KaSession)
    private fun processCodeDeclaration(expr: KtExpression) {
        processEscapes(expr)
        pushUnknown()
    }

    context(KaSession)
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

    context(KaSession)
    private fun processEscapes(expr: KtExpression) {
        val vars = mutableSetOf<DfaVariableValue>()
        val existingVars: Set<KtVariableDescriptor> = factory.values.asSequence()
            .filterIsInstance<DfaVariableValue>()
            .filter { v -> v.qualifier == null }
            .map { v -> v.descriptor }
            .filterIsInstance<KtVariableDescriptor>()
            .toSet()
        PsiTreeUtil.processElements(expr, KtSimpleNameExpression::class.java) { ref ->
            val nestedVar = KtVariableDescriptor.createFromSimpleName(factory, ref)
            if (nestedVar != null && existingVars.contains(nestedVar.descriptor)) {
                vars.addIfNotNull(nestedVar)
            }
            return@processElements true
        }
        if (vars.isNotEmpty()) {
            addInstruction(EscapeInstruction(vars))
        }
    }

    context(KaSession)
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

    context(KaSession)
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
        } else if (operandType.isInlineClass() && !expr.getKotlinType().isInlineClass()) {
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
            addInstruction(JvmPushInstruction(tempVariable, anchor))
            val endOffset = DeferredOffset()
            addInstruction(GotoInstruction(endOffset))
            setOffset(offset)
            addInstruction(PushValueInstruction(DfTypes.NULL, anchor))
            setOffset(endOffset)
        } else {
            val transfer = trapTracker.maybeTransferValue("kotlin.ClassCastException")
            addInstruction(EnsureInstruction(KotlinCastProblem(operand, expr), RelationType.IS, type, transfer))
            if (typeReference != null) {
                val castType = typeReference.type
                if (castType.toDfType() is DfPrimitiveType) {
                    addInstruction(UnwrapDerivedVariableInstruction(SpecialField.UNBOX))
                }
            }
        }
    }

    context(KaSession)
    private fun getTypeCheckDfType(typeReference: KtTypeReference?): DfType {
        if (typeReference == null) return DfType.TOP
        val kotlinType = typeReference.type
        if (kotlinType is KaErrorType || kotlinType is KaTypeParameterType) return DfType.TOP
        val result = if (kotlinType.isMarkedNullable) kotlinType.toDfType()
        else {
            // makeNullable to convert primitive to boxed
            val dfType = kotlinType.withNullability(KaTypeNullability.NULLABLE).toDfType().meet(DfTypes.NOT_NULL_OBJECT)
            if (dfType is DfReferenceType) dfType.dropSpecialField() else dfType
        }
        return if (result is DfReferenceType)
        // Convert Java to Kotlin types if necessary
            result.convert(KtClassDef.typeConstraintFactory(typeReference))
        else
            result
    }


    context(KaSession)
    private fun processBinaryExpression(expr: KtBinaryExpression) {
        val token = expr.operationToken
        val relation = relationFromToken(token)
        if (relation != null) {
            processBinaryRelationExpression(expr, relation, token == KtTokens.EXCLEQ || token == KtTokens.EQEQ)
            return
        }
        val leftKtType = expr.left?.getKotlinType()
        if (token === KtTokens.PLUS && (leftKtType?.isStringType == true || expr.right?.getKotlinType()?.isStringType == true)) {
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

    context(KaSession)
    private fun processInCheck(kotlinType: KaType?, range: KtExpression?, anchor: KotlinAnchor, negated: Boolean) {
        if (kotlinType != null && (kotlinType.isIntType || kotlinType.isLongType)) {
            if (range is KtBinaryExpression) {
                val op = range.operationReference.getReferencedNameAsName().asString()
                var relationType = when (op) {
                    "..", "rangeTo" -> RelationType.LE
                    "..<", "rangeUntil", "until" -> RelationType.LT
                    else -> null
                }
                if (relationType != null) {
                    val (left, right) = range.left to range.right
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

    context(KaSession)
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

    context(KaSession)
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
            (leftDfType is DfPrimitiveType && rightDfType is DfPrimitiveType)
        ) {
            val balancedType: KaType? = balanceType(leftType, rightType, forceEqualityByContent)
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
                addInstruction(UnwrapDerivedVariableInstruction(SpecialField.ENUM_ORDINAL))
                processExpression(right)
                addInstruction(UnwrapDerivedVariableInstruction(SpecialField.ENUM_ORDINAL))
                addInstruction(BooleanBinaryInstruction(relation, forceEqualityByContent, KotlinExpressionAnchor(expr)))
            } else if (leftConstraint.isExact(CommonClassNames.JAVA_LANG_STRING) &&
                rightConstraint.isExact(CommonClassNames.JAVA_LANG_STRING)
            ) {
                processExpression(right)
                addInstruction(BooleanBinaryInstruction(relation, forceEqualityByContent, KotlinExpressionAnchor(expr)))
            } else {
                // Overloaded >/>=/</<=: do not evaluate
                processExpression(right)
                addCall(expr, 2)
            }
        }
    }

    context(KaSession)
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

    context(KaSession)
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
            } else null
            val expectedType = if (lastIndex) expr.getKotlinType()?.toDfType() ?: DfType.TOP else DfType.TOP
            val indexType = idx.getKotlinType()
            if (indexType?.isIntType != true) {
                if (lastIndex && storedValue != null) {
                    processUnknownArrayStore(storedValue)
                } else {
                    addInstruction(EvalUnknownInstruction(anchor, 2, expectedType))
                    addInstruction(FlushFieldsInstruction())
                }
                continue
            }
            if (curType != null && curType.isArrayOrPrimitiveArray) {
                if (indexType.canBeNull()) {
                    addInstruction(UnwrapDerivedVariableInstruction(SpecialField.UNBOX))
                }
                val transfer = trapTracker.maybeTransferValue("kotlin.IndexOutOfBoundsException")
                val elementType = curType.arrayElementType
                if (lastIndex && storedValue != null) {
                    processExpression(storedValue)
                    addImplicitConversion(storedValue.getKotlinType(), curType.getJvmAwareArrayElementType())
                    addInstruction(ArrayStoreInstruction(anchor, KotlinArrayIndexProblem(SpecialField.ARRAY_LENGTH, idx), transfer, null))
                } else {
                    addInstruction(ArrayAccessInstruction(anchor, KotlinArrayIndexProblem(SpecialField.ARRAY_LENGTH, idx), transfer, null))
                    addImplicitConversion(curType.getJvmAwareArrayElementType(), elementType)
                }
                curType = elementType
            } else {
                when {
                    kotlinType?.isStringType == true -> {
                        if (indexType.canBeNull()) {
                            addInstruction(UnwrapDerivedVariableInstruction(SpecialField.UNBOX))
                        }
                        val transfer = trapTracker.maybeTransferValue("kotlin.IndexOutOfBoundsException")
                        addInstruction(EnsureIndexInBoundsInstruction(KotlinArrayIndexProblem(SpecialField.STRING_LENGTH, idx), transfer))
                        if (lastIndex && storedValue != null) {
                            processExpression(storedValue)
                            addInstruction(PopInstruction())
                        }
                        addInstruction(PushValueInstruction(DfTypes.typedObject(PsiTypes.charType(), Nullability.UNKNOWN), anchor))
                    }

                    kotlinType?.isSubtypeOf(StandardClassIds.List) == true -> {
                        if (indexType.canBeNull()) {
                            addInstruction(UnwrapDerivedVariableInstruction(SpecialField.UNBOX))
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

    context(KaSession)
    private fun processUnknownArrayStore(storedValue: KtExpression) {
        // stack before: <array_expression> <index_expression>
        processExpression(storedValue)
        val parent = storedValue.parent
        assert(parent is KtExpression) { "parent must be assignment expression, got ${parent::class}" }
        // process a[b] = c like a call with arguments a, b, c.
        addCall(parent as KtExpression, 3)
    }

    context(KaSession)
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
            (resultType.isLongType || resultType.isIntType)
        ) {
            val transfer: DfaControlTransferValue? = trapTracker.maybeTransferValue("kotlin.ArithmeticException")
            val zero = if (resultType.isLongType) DfTypes.longValue(0) else DfTypes.intValue(0)
            addInstruction(EnsureInstruction(null, RelationType.NE, zero, transfer, true))
        }
        addInstruction(NumericBinaryInstruction(mathOp, KotlinExpressionAnchor(expr)))
    }

    context(KaSession)
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

    context(KaSession)
    private fun KtExpressionWithLabel.targetLoop(): KtLoopExpression? {
        val label = getTargetLabel() as? KtLabelReferenceExpression
        return if (label == null) {
            parents.firstIsInstanceOrNull()
        } else {
            label.reference?.resolve() as? KtLoopExpression
        }
    }

    context(KaSession)
    private fun processLabeledJumpExpression(expr: KtExpressionWithLabel) {
        val targetLoop = expr.targetLoop()
        if (targetLoop == null || !PsiTreeUtil.isAncestor(context, targetLoop, false)) {
            addInstruction(ControlTransferInstruction(trapTracker.transferValue(DfaControlTransferValue.RETURN_TRANSFER)))
        } else {
            val body = if (expr is KtBreakExpression) targetLoop else targetLoop.body!!
            val transfer = createTransfer(body, body, factory.unknown, expr is KtBreakExpression)
            val transferValue = factory.controlTransfer(transfer, trapTracker.getTrapsInsideElement(body))
            addInstruction(ControlTransferInstruction(transferValue))
        }
    }

    private fun controlTransfer(target: TransferTarget, traps: FList<Trap>) {
        addInstruction(ControlTransferInstruction(factory.controlTransfer(target, traps)))
    }

    context(KaSession)
    private fun createTransfer(
        exitedStatement: PsiElement, blockToFlush: PsiElement, resultValue: DfaValue,
        exitBlock: Boolean = false
    ): InstructionTransfer {
        val varsToFlush = PsiTreeUtil.findChildrenOfType(
            blockToFlush,
            KtProperty::class.java
        ).map { property -> property.symbol.variableDescriptor() }
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

    context(KaSession)
    private fun processReturnExpression(expr: KtReturnExpression) {
        val returnedExpression = expr.returnedExpression
        processExpression(returnedExpression)
        val targetFunction = when {
          expr.labeledExpression != null -> expr.targetSymbol?.psi as? KtFunctionLiteral
          else -> findEffectiveTargetSymbol(expr)
        }
        if (targetFunction != null && PsiTreeUtil.isAncestor(context, targetFunction, true)) {
            val transfer: InstructionTransfer
            if (returnedExpression != null) {
                val retVar = flow.createTempVariable(returnedExpression.getKotlinType().toDfType())
                addInstruction(JvmAssignmentInstruction(null, retVar))
                transfer = createTransfer(targetFunction, targetFunction, retVar)
            } else {
                transfer = createTransfer(targetFunction, targetFunction, factory.unknown)
            }
            addInstruction(
                ControlTransferInstruction(
                    factory.controlTransfer(
                        transfer,
                        trapTracker.getTrapsInsideElement(targetFunction)
                    )
                )
            )
            return
        }
        addInstruction(ReturnInstruction(factory, trapTracker.trapStack(), expr))
    }

    /**
     * For code like `return x.let { return y }`, we assume that `return y` returns from
     * the `let`, rather than from the parent method instead.
     * This is semantically equivalent and allows to get rid of some noise warnings.
     */
    context(KaSession)
    private fun findEffectiveTargetSymbol(expression: KtReturnExpression): KtFunctionLiteral? {
        val parentFunctionLiteral = expression.parentOfType<KtFunctionLiteral>()
        if (parentFunctionLiteral == null || !context.isAncestor(parentFunctionLiteral)) return null
        val lambda = parentFunctionLiteral.parent as? KtLambdaExpression ?: return null
        val lambdaArg = lambda.parent as? KtValueArgument ?: return null
        val call = lambdaArg.parent as? KtCallExpression ?: return null
        val functionCall: KaFunctionCall<*> = call.resolveToCall()?.singleFunctionCallOrNull() ?: return null
        val target: KaNamedFunctionSymbol = functionCall.partiallyAppliedSymbol.symbol as? KaNamedFunctionSymbol ?: return null
        val functionName = target.name.asString()
        if (functionName != LET && functionName != RUN) return null
        if (StandardNames.BUILT_INS_PACKAGE_FQ_NAME != target.callableId?.packageName) return null
        var outerExpr: KtExpression = call.parent as? KtQualifiedExpression ?: return null
        var outerExprParent = outerExpr.parent
        while (outerExprParent is KtBinaryExpression && AND_OR_ELVIS_TOKENS.contains(outerExprParent.operationToken) && 
            outerExprParent.right == outerExpr) {
            outerExpr = outerExprParent
            outerExprParent = outerExpr.parent
        }
        if (outerExprParent is KtNamedFunction) return parentFunctionLiteral
        if (outerExprParent is KtReturnExpression && outerExprParent.labeledExpression == null) return parentFunctionLiteral
        return null
    }

    context(KaSession)
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

    context(KaSession)
    private fun processWhenExpression(expr: KtWhenExpression) {
        val subjectExpression = expr.subjectExpression
        val dfVar: DfaVariableValue?
        val kotlinType: KaType?
        if (subjectExpression == null) {
            dfVar = null
            kotlinType = null
        } else {
            processExpression(subjectExpression)
            val subjectVariable = expr.subjectVariable
            if (subjectVariable != null) {
                kotlinType = subjectVariable.returnType
                dfVar = factory.varFactory.createVariableValue(subjectVariable.symbol.variableDescriptor())
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

    context(KaSession)
    private fun processWhenCondition(dfVar: DfaVariableValue?, dfVarType: KaType?, condition: KtWhenCondition) {
        when (condition) {
            is KtWhenConditionWithExpression -> {
                val expr = condition.expression
                processExpression(expr)
                val exprType = expr?.getKotlinType()
                if (dfVar != null) {
                    val balancedType = balanceType(exprType, dfVarType, true)
                    addImplicitConversion(exprType, balancedType)
                    addInstruction(JvmPushInstruction(dfVar, null))
                    addImplicitConversion(dfVarType, balancedType)
                    addInstruction(BooleanBinaryInstruction(RelationType.EQ, true, KotlinWhenConditionAnchor(condition)))
                } else if (exprType?.canBeNull() == true) {
                    addInstruction(UnwrapDerivedVariableInstruction(SpecialField.UNBOX))
                }
            }

            is KtWhenConditionIsPattern -> {
                val type = getTypeCheckDfType(condition.typeReference)
                if (dfVar == null || type == DfType.TOP) {
                    pushUnknown()
                } else {
                    addInstruction(JvmPushInstruction(dfVar, null))
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
                    addInstruction(JvmPushInstruction(dfVar, null))
                } else {
                    pushUnknown()
                }
                processInCheck(dfVarType, condition.rangeExpression, KotlinWhenConditionAnchor(condition), condition.isNegated)
            }

            else -> broken = true
        }
    }

    context(KaSession)
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

    context(KaSession)
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

    data class KotlinCatchClauseDescriptor(val clause: KtCatchClause) : CatchClauseDescriptor {
        override fun parameter(): VariableDescriptor? {
            val parameter = clause.catchParameter ?: return null
            return analyze(clause) { parameter.symbol.variableDescriptor() }
        }

        override fun constraints(): MutableList<TypeConstraint> {
            val parameter = clause.catchParameter ?: return mutableListOf()
            return mutableListOf(analyze(clause) { TypeConstraint.fromDfType(parameter.returnType.toDfType()) })
        }
    }

    context(KaSession)
    private fun processDestructuringDeclaration(expr: KtDestructuringDeclaration) {
        processExpression(expr.initializer)
        for (entry in expr.entries) {
            addInstruction(
                FlushVariableInstruction(
                    factory.varFactory.createVariableValue(
                        entry.symbol.variableDescriptor()
                    )
                )
            )
        }
    }

    context(KaSession)
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

    context(KaSession)
    private fun processForExpression(expr: KtForExpression) {
        inlinedBlock(expr) {
            val parameter = expr.loopParameter
            if (parameter == null) {
                broken = true
                return@inlinedBlock
            }
            val parameterVar = factory.varFactory.createVariableValue(parameter.symbol.variableDescriptor())
            val parameterType = parameter.returnType
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

    context(KaSession)
    private fun flushParameter(parameter: KtParameter) {
        val destructuringDeclaration = parameter.destructuringDeclaration
        if (destructuringDeclaration != null) {
            for (entry in destructuringDeclaration.entries) {
                addInstruction(
                    FlushVariableInstruction(
                        factory.varFactory.createVariableValue(
                            entry.symbol.variableDescriptor()
                        )
                    )
                )
            }
        } else {
            addInstruction(
                FlushVariableInstruction(
                    factory.varFactory.createVariableValue(
                        parameter.symbol.variableDescriptor()
                    )
                )
            )
        }
    }

    context(KaSession)
    private fun processForRange(expr: KtForExpression, parameterVar: DfaVariableValue, parameterType: KaType?): () -> Unit {
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
                                    kotlinType.isSubtypeOf(StandardClassIds.Collection) -> SpecialField.COLLECTION_SIZE
                                    kotlinType.isArrayOrPrimitiveArray -> SpecialField.ARRAY_LENGTH
                                    else -> null
                                }
                                if (sf != null) {
                                    val size = sf.createValue(factory, dfVar)
                                    return rangeFunction(
                                        expr, parameterVar, factory.fromDfType(DfTypes.intValue(0)),
                                        RelationType.GE, size, RelationType.LT
                                    )
                                }
                            }
                        }
                    }
                }

                is KtBinaryExpression -> {
                    val op = range.operationReference.getReferencedNameAsName().asString()
                    val (leftRelation, rightRelation) = when (op) {
                        "..", "rangeTo" -> RelationType.GE to RelationType.LE
                        "..<", "rangeUntil", "until" -> RelationType.GE to RelationType.LT
                        "downTo" -> RelationType.LE to RelationType.GE
                        else -> (null to null)
                    }
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
                        addInstruction(JvmPushInstruction(lengthField.createValue(factory, collectionVar), null))
                        addInstruction(PushValueInstruction(DfTypes.intValue(0)))
                        addInstruction(BooleanBinaryInstruction(RelationType.GT, false, null))
                        pushUnknown()
                        addInstruction(BooleanAndOrInstruction(false, KotlinForVisitedAnchor(expr)))
                    }
                }

                SpecialField.UNBOX, SpecialField.OPTIONAL_VALUE, SpecialField.ENUM_ORDINAL, SpecialField.CONSUMED_STREAM,
                SpecialField.INSTANTIABLE_CLASS, null -> {
                }
            }
        }
        addInstruction(PopInstruction())
        return { pushUnknown() }
    }

    context(KaSession)
    private fun findSpecialField(type: KaType?): SpecialField? {
        type ?: return null
        return when {
            type.isEnum() -> SpecialField.ENUM_ORDINAL
            type.isArrayOrPrimitiveArray -> SpecialField.ARRAY_LENGTH
            type.isSubtypeOf(StandardClassIds.Collection) ||
                    type.isSubtypeOf(StandardClassIds.Map) -> SpecialField.COLLECTION_SIZE

            type.isStringType -> SpecialField.STRING_LENGTH
            else -> null
        }
    }

    context(KaSession)
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

    context(KaSession)
    private fun rangeFunction(
        expr: KtForExpression,
        parameterVar: DfaVariableValue,
        leftVar: DfaValue,
        leftRelation: RelationType,
        rightVar: DfaValue,
        rightRelation: RelationType
    ): () -> Unit = {
        val forAnchor = KotlinForVisitedAnchor(expr)
        addInstruction(JvmPushInstruction(parameterVar, null))
        addInstruction(JvmPushInstruction(leftVar, null))
        addInstruction(BooleanBinaryInstruction(leftRelation, false, null))
        val offset = DeferredOffset()
        addInstruction(ConditionalGotoInstruction(offset, DfTypes.FALSE))
        addInstruction(JvmPushInstruction(parameterVar, null))
        addInstruction(JvmPushInstruction(rightVar, null))
        addInstruction(BooleanBinaryInstruction(rightRelation, false, forAnchor))
        val finalOffset = DeferredOffset()
        addInstruction(GotoInstruction(finalOffset))
        setOffset(offset)
        addInstruction(PushValueInstruction(DfTypes.FALSE, forAnchor))
        setOffset(finalOffset)
    }

    context(KaSession)
    private inline fun inlinedBlock(element: KtElement, fn: () -> Unit) {
        // Transfer value is pushed to avoid emptying stack beyond this point
        trapTracker.pushTrap(InsideInlinedBlockTrap(element))
        addInstruction(JvmPushInstruction(factory.controlTransfer(DfaControlTransferValue.RETURN_TRANSFER, FList.emptyList()), null))

        fn()

        trapTracker.popTrap(InsideInlinedBlockTrap::class.java)
        // Pop transfer value
        addInstruction(PopInstruction())
    }

    context(KaSession)
    private fun processCallableReference(expr: KtCallableReferenceExpression) {
        processExpression(expr.receiverExpression)
        addInstruction(KotlinCallableReferenceInstruction(expr, expr.getKotlinType().toDfType()))
    }

    context(KaSession)
    private fun processClassLiteralExpression(expr: KtClassLiteralExpression) {
        val kotlinType = expr.getKotlinType()
        val receiver = expr.receiverExpression
        if (kotlinType is KaClassType) {
            if (receiver is KtSimpleNameExpression && receiver.mainReference.resolve() is KtClass) {
                val arguments = kotlinType.typeArguments
                if (arguments.size == 1) {
                    val kType = arguments[0].type?.expandedSymbol?.classDef()
                    val kClassPsiType = TypeConstraint.fromDfType(kotlinType.toDfType())
                    if (kType != null && kClassPsiType != TypeConstraints.TOP) {
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

    context(KaSession)
    private fun processDeclaration(variable: KtProperty) {
        val initializer = variable.initializer
        if (initializer == null) {
            pushUnknown()
            return
        }
        val dfaVariable = factory.varFactory.createVariableValue(variable.symbol.variableDescriptor())
        if (variable.isLocal && !variable.isVar && variable.returnType.isBooleanType) {
            // Boolean true/false constant: do not track; might be used as a feature knob or explanatory variable
            if (initializer.node?.elementType == KtNodeTypes.BOOLEAN_CONSTANT) {
                pushUnknown()
                return
            }
        }
        processExpression(initializer)
        addImplicitConversion(initializer, variable.returnType)
        addInstruction(JvmAssignmentInstruction(KotlinExpressionAnchor(variable), dfaVariable))
    }

    context(KaSession)
    private fun processReferenceExpression(expr: KtSimpleNameExpression, qualifierOnStack: Boolean = false) {
        val dfVar = KtVariableDescriptor.createFromSimpleName(factory, expr)
        if (dfVar != null) {
            if (qualifierOnStack) {
                addInstruction(PopInstruction())
            }
            addInstruction(JvmPushInstruction(dfVar, KotlinExpressionAnchor(expr)))
            var realExpr: KtExpression = expr
            while (true) {
                val parent = realExpr.parent
                if (parent is KtQualifiedExpression && parent.selectorExpression == realExpr) {
                    realExpr = parent
                } else break
            }
            val exprType = realExpr.getKotlinType()
            val declaredType = when (val desc = dfVar.descriptor) {
                is KtVariableDescriptor -> desc.pointer.restoreSymbol()?.returnType
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

    context(KaSession)
    private fun processQualifiedReferenceExpression(expr: KtQualifiedExpression) {
        val receiver = expr.receiverExpression
        processExpression(receiver)
        val offset = DeferredOffset()
        if (expr is KtSafeQualifiedExpression) {
            val receiverType = receiver.getKotlinType()
            addImplicitConversion(receiverType, receiverType?.withNullability(KaTypeNullability.NULLABLE))
            addInstruction(DupInstruction())
            addInstruction(ConditionalGotoInstruction(offset, DfTypes.NULL))
        }
        val selector = expr.selectorExpression
        selector?.let(flow::startElement)
        if (!pushJavaClassField(receiver, selector, expr)) {
            val specialField = findSpecialField(expr)
            if (specialField != null) {
                addInstruction(UnwrapDerivedVariableInstruction(specialField))
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

    context(KaSession)
    private fun pushJavaClassField(receiver: KtExpression, selector: KtExpression?, expr: KtQualifiedExpression): Boolean {
        if (selector == null || !selector.textMatches("java")) return false
        if (receiver.getKotlinType()?.isSubtypeOf(StandardClassIds.KClass) != true) return false
        val kotlinType = expr.getKotlinType() ?: return false
        val classType = TypeConstraint.fromDfType(kotlinType.toDfType())
        if (!classType.isExact(CommonClassNames.JAVA_LANG_CLASS)) return false
        addInstruction(KotlinClassToJavaClassInstruction(KotlinExpressionAnchor(expr), classType))
        return true
    }

    context(KaSession)
    private fun processCallExpression(expr: KtCallExpression, qualifierOnStack: Boolean = false) {
        val call: KaSuccessCallInfo? = expr.resolveToCall() as? KaSuccessCallInfo
        val updatedQualifierOnStack = if (!qualifierOnStack && call != null) {
            tryPushImplicitQualifier(call)
        } else {
            qualifierOnStack
        }
        var argCount = pushCallArguments(expr, call)
        if (inlineKnownMethod(expr, argCount, updatedQualifierOnStack)) return
        val lambda = getInlineableLambda(expr)
        if (lambda != null) {
            if (updatedQualifierOnStack && inlineKnownLambdaCall(expr, lambda.lambda)) return
            val kind = getLambdaOccurrenceRange(expr, lambda.descriptor)
            inlineLambda(lambda.lambda, kind)
        } else {
            for (lambdaArg in expr.lambdaArguments) {
                processExpression(lambdaArg.getLambdaExpression())
                argCount++
            }
        }

        addCall(expr, argCount, updatedQualifierOnStack)
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun getLambdaOccurrenceRange(expr: KtCallExpression, parameter: KaValueParameterSymbol): EventOccurrencesRange {
        val functionCall = expr.resolveToCall()?.singleFunctionCallOrNull() ?: return EventOccurrencesRange.UNKNOWN
        val functionSymbol = functionCall.partiallyAppliedSymbol.symbol as? KaNamedFunctionSymbol ?: return EventOccurrencesRange.UNKNOWN
        val callEffect = functionSymbol.contractEffects
            .singleOrNull { e -> e is KaContractCallsInPlaceContractEffectDeclaration && e.valueParameterReference.parameterSymbol == parameter }
                as? KaContractCallsInPlaceContractEffectDeclaration
        if (callEffect != null) {
            return callEffect.occurrencesRange
        }
        return EventOccurrencesRange.UNKNOWN
    }

    context(KaSession)
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
            kind != EventOccurrencesRange.AT_LEAST_ONCE
        ) {
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

    context(KaSession)
    private fun inlineKnownLambdaCall(expr: KtCallExpression, lambda: KtLambdaExpression): Boolean {
        // TODO: non-qualified methods (run, repeat)
        // TODO: collection methods (forEach, map, etc.)
        val resolvedCall = expr.resolveToCall()?.singleFunctionCallOrNull() ?: return false
        val symbol = resolvedCall.partiallyAppliedSymbol.symbol as? KaNamedFunctionSymbol ?: return false
        val packageName = symbol.callableId?.packageName ?: return false
        val bodyExpression = lambda.bodyExpression
        val receiver = (expr.parent as? KtQualifiedExpression)?.receiverExpression
        if (packageName == StandardNames.BUILT_INS_PACKAGE_FQ_NAME && resolvedCall.argumentMapping.size == 1) {
            val name = symbol.name.asString()
            if (name == LET || name == ALSO || name == TAKE_IF || name == TAKE_UNLESS || name == APPLY || name == RUN) {
                val parameter = (if (name == APPLY || name == RUN)
                    KtVariableDescriptor.getLambdaReceiver(factory, lambda)
                else
                    KtVariableDescriptor.getSingleLambdaParameter(factory, lambda)) ?: return false
                // qualifier is on stack
                val receiverType = receiver?.getKotlinType()
                val argType =
                    if (expr.parent is KtSafeQualifiedExpression) receiverType?.withNullability(KaTypeNullability.NON_NULLABLE) else receiverType
                addImplicitConversion(receiver, argType)
                addInstruction(JvmAssignmentInstruction(null, parameter))
                val functionLiteral = lambda.functionLiteral
                when (name) {
                    LET, RUN -> {
                        addInstruction(PopInstruction())
                        val lambdaResultType = (functionLiteral.functionType as? KaFunctionType)?.returnType
                        val result = flow.createTempVariable(lambdaResultType.toDfType())
                        inlinedBlock(lambda) {
                            processExpression(bodyExpression)
                            flow.finishElement(functionLiteral)
                            addInstruction(JvmAssignmentInstruction(null, result))
                            addInstruction(PopInstruction())
                        }
                        addInstruction(JvmPushInstruction(result, null))
                        addImplicitConversion(lambdaResultType, expr.getKotlinType())
                    }

                    ALSO, APPLY -> {
                        inlinedBlock(lambda) {
                            processExpression(bodyExpression)
                            flow.finishElement(functionLiteral)
                            addInstruction(PopInstruction())
                        }
                        addImplicitConversion(argType, expr.getKotlinType())
                        addInstruction(ResultOfInstruction(KotlinExpressionAnchor(expr)))
                    }

                    TAKE_IF, TAKE_UNLESS -> {
                        val result = flow.createTempVariable(DfTypes.BOOLEAN)
                        inlinedBlock(lambda) {
                            processExpression(bodyExpression)
                            flow.finishElement(functionLiteral)
                            addInstruction(JvmAssignmentInstruction(null, result))
                            addInstruction(PopInstruction())
                        }
                        addInstruction(JvmPushInstruction(result, null))
                        val offset = DeferredOffset()
                        addInstruction(ConditionalGotoInstruction(offset, DfTypes.booleanValue(name == TAKE_IF)))
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

    context(KaSession)
    private fun inlineKnownMethod(expr: KtCallExpression, argCount: Int, qualifierOnStack: Boolean): Boolean {
        if (argCount == 0 && qualifierOnStack) {
            val functionCall: KaFunctionCall<*> = expr.resolveToCall()?.singleFunctionCallOrNull() ?: return false
            val target: KaNamedFunctionSymbol = functionCall.partiallyAppliedSymbol.symbol as? KaNamedFunctionSymbol ?: return false
            val name = target.name.asString()
            if (name == "isEmpty" || name == "isNotEmpty") {
                val callableId = target.callableId
                if (callableId != null && callableId.packageName.asString() == "kotlin.collections") {
                    addInstruction(UnwrapDerivedVariableInstruction(SpecialField.COLLECTION_SIZE))
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

    context(KaSession)
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

    context(KaSession)
    private fun pushCallArguments(expr: KtCallExpression, callInfo: KaSuccessCallInfo?): Int {
        val functionCall = callInfo?.call as? KaFunctionCall<*> ?: return pushUnresolvedCallArguments(expr)
        var argCount = 0
        var varArgCount = 0
        var varArgType: DfType = DfType.BOTTOM

        for ((argExpr, signature) in functionCall.argumentMapping) {
            val parameterSymbol = signature.symbol
            val parent = argExpr.parent
            if (parameterSymbol.isVararg && (parent as? KtValueArgument)?.isSpread != true) {
                varArgCount++
                varArgType = parameterSymbol.returnType.toDfType()
            } else {
                if (varArgCount > 0) {
                    addInstruction(FoldArrayInstruction(null, varArgType, varArgCount))
                    argCount -= varArgCount - 1
                    varArgCount = 0
                }
            }
            if (parent !is KtLambdaArgument) {
                processExpression(argExpr)
                argCount++
            }
        }
        if (varArgCount > 0) {
            addInstruction(FoldArrayInstruction(null, varArgType, varArgCount))
            argCount -= varArgCount - 1
        }
        return argCount
    }

    context(KaSession)
    private fun tryPushImplicitQualifier(callInfo: KaSuccessCallInfo): Boolean {
        val call = callInfo.call as? KaFunctionCall<*>
        val receiver = (call?.partiallyAppliedSymbol?.dispatchReceiver as? KaImplicitReceiverValue)?.symbol
        if (receiver is KaReceiverParameterSymbol) {
            val psi = receiver.psi
            if (psi is KtFunctionLiteral) {
                val varDescriptor = KtLambdaThisVariableDescriptor(psi, receiver.returnType.toDfType())
                addInstruction(PushInstruction(factory.varFactory.createVariableValue(varDescriptor), null))
                return true
            }
        }
        return false
    }

    context(KaSession)
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

    context(KaSession)
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

    context(KaSession)
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

    context(KaSession)
    private fun processThisExpression(expr: KtThisExpression) {
        val exprType = expr.getKotlinType()
        val symbol = ((expr.instanceReference as? KtNameReferenceExpression)?.reference as? KtReference)?.resolveToSymbol()
        var varDesc: VariableDescriptor? = null
        var declType: KaType? = null
        if (symbol is KaReceiverParameterSymbol && exprType != null) {
            val function = symbol.psi as? KtFunctionLiteral
            declType = symbol.returnType
            if (function != null) {
                varDesc = KtLambdaThisVariableDescriptor(function, declType.toDfType())
            } else {
                if (declType is KaClassType) {
                    val classDef = declType.expandedSymbol?.classDef()
                    if (classDef != null) {
                        varDesc = KtThisDescriptor(classDef, symbol.owningCallableSymbol.name?.asString())
                    }
                }
            }
        } 
        else if (symbol is KaClassSymbol && exprType != null) {
            varDesc = KtThisDescriptor(symbol.classDef())
        }
        if (varDesc != null) {
            addInstruction(JvmPushInstruction(factory.varFactory.createVariableValue(varDesc), KotlinExpressionAnchor(expr)))
            addImplicitConversion(declType, exprType)
        }
        else {
            addInstruction(PushValueInstruction(exprType.toDfType(), KotlinExpressionAnchor(expr)))
        }
    }


    private fun addCall(expr: KtExpression, args: Int, qualifierOnStack: Boolean = false) {
        val transfer = trapTracker.maybeTransferValue("kotlin.Throwable")
        addInstruction(KotlinFunctionCallInstruction(expr, args, qualifierOnStack, transfer))
    }

    private fun mayCompareByContent(leftDfType: DfType, rightDfType: DfType): Boolean {
        if (leftDfType == DfTypes.NULL || rightDfType == DfTypes.NULL) return true
        if (leftDfType is DfPrimitiveType || rightDfType is DfPrimitiveType) return true
        val constraint = TypeConstraint.fromDfType(leftDfType)
        if (constraint.isComparedByEquals || constraint.isArray || constraint.isEnum) return true
        if (!constraint.isExact) return false
        // TODO: get rid of PSI: need to check whether equals() is not overridden and goes directly from Object
        val cls = PsiUtil.resolveClassInClassTypeOnly(constraint.getPsiType(factory.project)) ?: return false
        val equalsMethodSignature =
            MethodSignatureUtil.createMethodSignature("equals", arrayOf(TypeUtils.getObjectType(context)), arrayOf(), PsiSubstitutor.EMPTY)
        val method = MethodSignatureUtil.findMethodBySignature(cls, equalsMethodSignature, true)
        return method?.containingClass?.qualifiedName == CommonClassNames.JAVA_LANG_OBJECT
    }

    context(KaSession)
    private fun addImplicitConversion(expression: KtExpression?, expectedType: KaType?) {
        addImplicitConversion(expression?.getKotlinType(), expectedType)
    }

    context(KaSession)
    private fun addImplicitConversion(actualType: KaType?, expectedType: KaType?) {
        actualType ?: return
        expectedType ?: return
        if (actualType == expectedType) return
        val actualDfType = actualType.toDfType()
        val expectedDfType = expectedType.toDfType()
        if (actualType.isInlineClass() && !expectedType.isInlineClass()) {
            addInstruction(PopInstruction())
            addInstruction(PushValueInstruction(actualDfType))
        }
        if (actualDfType !is DfPrimitiveType && expectedDfType is DfPrimitiveType) {
            addInstruction(UnwrapDerivedVariableInstruction(SpecialField.UNBOX))
        } else if (expectedDfType !is DfPrimitiveType && actualDfType is DfPrimitiveType) {
            val dfType = actualType.withNullability(KaTypeNullability.NULLABLE).toDfType().meet(DfTypes.NOT_NULL_OBJECT)
            addInstruction(WrapDerivedVariableInstruction(expectedType.toDfType().meet(dfType), SpecialField.UNBOX))
        }
        if (actualDfType is DfPrimitiveType && expectedDfType is DfPrimitiveType) {
            addInstruction(PrimitiveConversionInstruction(expectedType.toPsiPrimitiveType(), null))
        }
    }

    context(KaSession)
    private fun KaType?.isInlineClass() =
        ((this as? KaClassType)?.expandedSymbol as? KaNamedClassSymbol)?.isInline == true

    context(KaSession)
    private fun balanceType(leftType: KaType?, rightType: KaType?, forceEqualityByContent: Boolean): KaType? = when {
        leftType == null || rightType == null -> null
        leftType.isNothingType && leftType.isMarkedNullable -> rightType.withNullability(KaTypeNullability.NULLABLE)
        rightType.isNothingType && rightType.isMarkedNullable -> leftType.withNullability(KaTypeNullability.NULLABLE)
        !forceEqualityByContent -> balanceType(leftType, rightType)
        leftType.isSubtypeOf(rightType) -> rightType
        rightType.isSubtypeOf(leftType) -> leftType
        else -> null
    }

    context(KaSession)
    private fun balanceType(left: KaType?, right: KaType?): KaType? {
        if (left == null || right == null) return null
        if (left == right) return left
        if (left.canBeNull() && !right.canBeNull()) {
            return balanceType(left.withNullability(KaTypeNullability.NON_NULLABLE), right)
        }
        if (!left.canBeNull() && right.canBeNull()) {
            return balanceType(left, right.withNullability(KaTypeNullability.NON_NULLABLE))
        }
        if (left.isDoubleType) return left
        if (right.isDoubleType) return right
        if (left.isFloatType) return left
        if (right.isFloatType) return right
        if (left.isLongType) return left
        if (right.isLongType) return right
        // The 'null' means no balancing is necessary
        return null
    }


    companion object {
        private val LOG = logger<KtControlFlowBuilder>()
        private val ASSIGNMENT_TOKENS =
            TokenSet.create(KtTokens.EQ, KtTokens.PLUSEQ, KtTokens.MINUSEQ, KtTokens.MULTEQ, KtTokens.DIVEQ, KtTokens.PERCEQ)
        private val AND_OR_ELVIS_TOKENS = TokenSet.create(KtTokens.ANDAND, KtTokens.OROR, KtTokens.ELVIS)
        private val unsupported = ConcurrentHashMap.newKeySet<String>()
        private const val LET = "let"
        private const val RUN = "run"
        private const val ALSO = "also"
        private const val APPLY = "apply"
        private const val TAKE_IF = "takeIf"
        private const val TAKE_UNLESS = "takeUnless"
    }
}
