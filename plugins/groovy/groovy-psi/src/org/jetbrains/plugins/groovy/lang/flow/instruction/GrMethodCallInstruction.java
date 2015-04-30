package org.jetbrains.plugins.groovy.lang.flow.instruction;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PropertyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;

import java.util.Arrays;
import java.util.Map;

public class GrMethodCallInstruction<V extends GrInstructionVisitor<V>> extends Instruction<V> {

  private final @NotNull GrExpression myCall;

  private final @NotNull GrNamedArgument[] myNamedArguments;
  private final @NotNull GrExpression[] myExpressionArguments;
  private final @NotNull GrClosableBlock[] myClosureArguments;

  private final @Nullable PsiType myReturnType;
  private final @Nullable PsiMethod myTargetMethod;

  private final boolean myShouldFlushFields;

  private final @Nullable Map<GrExpression, Pair<PsiParameter, PsiType>> argumentsToParameters;
  private final @Nullable DfaValue myPrecalculatedReturnValue;


  public GrMethodCallInstruction(@NotNull GrReferenceExpression propertyAccess,
                                 @NotNull PsiMethod property,
                                 @Nullable GrExpression rValue) {
    myCall = propertyAccess;
    myNamedArguments = GrNamedArgument.EMPTY_ARRAY;
    myExpressionArguments = rValue == null ? GrExpression.EMPTY_ARRAY : new GrExpression[]{rValue};
    myClosureArguments = GrClosableBlock.EMPTY_ARRAY;

    myReturnType = property.getReturnType();
    myTargetMethod = property;

    myShouldFlushFields = false;
    argumentsToParameters = null;
    myPrecalculatedReturnValue = null;
  }

  public GrMethodCallInstruction(@NotNull GrExpression call,
                                 @NotNull GrNamedArgument[] namedArguments,
                                 @NotNull GrExpression[] expressionArguments,
                                 @NotNull GrClosableBlock[] closureArguments,
                                 @NotNull GroovyResolveResult result) {
    myCall = call;
    myNamedArguments = namedArguments;
    myExpressionArguments = expressionArguments;
    myClosureArguments = closureArguments;
    myTargetMethod = (PsiMethod)result.getElement();
    assert myTargetMethod != null;
    myReturnType = myTargetMethod.getReturnType();
    myShouldFlushFields = !(call instanceof GrNewExpression && myReturnType != null && myReturnType.getArrayDimensions() > 0)
                          && !isPureCall(myTargetMethod);
    argumentsToParameters = GrClosureSignatureUtil.mapArgumentsToParameters(
      result, call, false, false, myNamedArguments, myExpressionArguments, myClosureArguments
    );
    myPrecalculatedReturnValue = null;
  }

  public GrMethodCallInstruction(@NotNull GrExpression call,
                                 @NotNull GrExpression[] expressionArguments,
                                 @NotNull GroovyResolveResult result) {
    this(call, GrNamedArgument.EMPTY_ARRAY, expressionArguments, GrClosableBlock.EMPTY_ARRAY, result);
  }

  public GrMethodCallInstruction(@NotNull GrCallExpression call, @Nullable DfaValue precalculatedReturnValue) {
    final GroovyResolveResult result = call.advancedResolve();

    myCall = call;
    myNamedArguments = call.getNamedArguments();
    myExpressionArguments = call.getExpressionArguments();
    myClosureArguments = call.getClosureArguments();

    myReturnType = myCall.getType();
    myTargetMethod = (PsiMethod)result.getElement();

    myShouldFlushFields = !(call instanceof GrNewExpression && myReturnType != null && myReturnType.getArrayDimensions() > 0)
                          && !isPureCall(myTargetMethod);
    argumentsToParameters = GrClosureSignatureUtil.mapArgumentsToParameters(
      result, call, false, false, call.getNamedArguments(), myExpressionArguments, myClosureArguments
    );
    myPrecalculatedReturnValue = precalculatedReturnValue;
  }

  public Nullness getParameterNullability(GrExpression e) {
    Pair<PsiParameter, PsiType> p = argumentsToParameters == null ? null : argumentsToParameters.get(e);
    PsiParameter parameter = p == null ? null : p.first;
    PsiType type = p == null ? null : p.second;
    if (parameter == null || type == null) return Nullness.UNKNOWN;
    return DfaPsiUtil.getElementNullability(type, parameter);
  }

  @NotNull
  public GrExpression getCall() {
    return myCall;
  }

  @NotNull
  public GrNamedArgument[] getNamedArguments() {
    return myNamedArguments;
  }

  @NotNull
  public GrExpression[] getExpressionArguments() {
    return myExpressionArguments;
  }

  @NotNull
  public GrClosableBlock[] getClosureArguments() {
    return myClosureArguments;
  }

  @Nullable
  public PsiType getReturnType() {
    return myReturnType;
  }

  @Nullable
  public PsiMethod getTargetMethod() {
    return myTargetMethod;
  }

  public boolean shouldFlushFields() {
    return myShouldFlushFields;
  }

  @Nullable
  public DfaValue getPrecalculatedReturnValue() {
    return myPrecalculatedReturnValue;
  }

  @Override
  public DfaInstructionState<V>[] accept(@NotNull DfaMemoryState stateBefore, @NotNull V visitor) {
    return visitor.visitMethodCallGroovy(this, stateBefore);
  }

  public String toString() {
    return "CALL METHOD " +
           myReturnType +
           " " +
           myTargetMethod.getName() +
           "(" +
           Arrays.toString(myNamedArguments) +
           ":" +
           Arrays.toString(myExpressionArguments) +
           ":" +
           Arrays.toString(myClosureArguments) +
           ")";
  }

  private static boolean isPureCall(PsiMethod myTargetMethod) {
    if (myTargetMethod == null) return false;
    return ControlFlowAnalyzer.isPure(myTargetMethod) || PropertyUtil.isSimplePropertyGetter(myTargetMethod);
  }
}
