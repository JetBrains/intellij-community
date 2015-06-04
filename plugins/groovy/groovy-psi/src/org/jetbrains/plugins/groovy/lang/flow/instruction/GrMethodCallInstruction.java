package org.jetbrains.plugins.groovy.lang.flow.instruction;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PropertyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.flow.GrDfaUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;

import java.util.Arrays;
import java.util.Map;

public class GrMethodCallInstruction extends GrInstruction {

  private final @NotNull PsiElement myCall;

  private final @NotNull GrNamedArgument[] myNamedArguments;
  private final @NotNull GrExpression[] myExpressionArguments;
  private final @NotNull GrClosableBlock[] myClosureArguments;

  private final @Nullable PsiType myReturnType;
  private final @Nullable PsiMethod myTargetMethod;

  private final boolean myShouldFlushFields;

  private final @Nullable Map<GrExpression, Pair<PsiParameter, PsiType>> argumentsToParameters;
  private final @Nullable DfaValue myPrecalculatedReturnValue;
  private final boolean myRequiresNotNullQualifier;

  public GrMethodCallInstruction(@NotNull GrReferenceExpression propertyGetter,
                                 @NotNull PsiMethod property,
                                 @Nullable DfaValue precalculatedReturnValue) {
    myCall = propertyGetter;
    myNamedArguments = GrNamedArgument.EMPTY_ARRAY;
    myExpressionArguments = GrExpression.EMPTY_ARRAY;
    myClosureArguments = GrClosableBlock.EMPTY_ARRAY;

    myReturnType = property.getReturnType();
    myTargetMethod = property;

    myShouldFlushFields = false;
    argumentsToParameters = null;
    myPrecalculatedReturnValue = precalculatedReturnValue;
    myRequiresNotNullQualifier = true;
  }

  public GrMethodCallInstruction(@NotNull PsiElement call,
                                 @NotNull GrNamedArgument[] namedArguments,
                                 @NotNull GrExpression[] expressionArguments,
                                 @NotNull GrClosableBlock[] closureArguments,
                                 @NotNull GroovyResolveResult result,
                                 @Nullable DfaValue precalculatedReturnValue) {
    myCall = call;
    myNamedArguments = namedArguments;
    myExpressionArguments = expressionArguments;
    myClosureArguments = closureArguments;
    myTargetMethod = result.getElement() instanceof PsiMethod ? (PsiMethod)result.getElement() : null;
    myReturnType = myTargetMethod == null ? null : myTargetMethod.getReturnType();
    myShouldFlushFields = !(call instanceof GrNewExpression && myReturnType != null && myReturnType.getArrayDimensions() > 0)
                          && !isPureCall(myTargetMethod);
    argumentsToParameters = myTargetMethod instanceof GrAccessorMethod ? null : GrClosureSignatureUtil.mapArgumentsToParameters(
      result, call, false, false, myNamedArguments, myExpressionArguments, myClosureArguments
    );
    myPrecalculatedReturnValue = precalculatedReturnValue;
    myRequiresNotNullQualifier = myTargetMethod == null || GrDfaUtil.requiresNotNullQualifier(myTargetMethod);
  }

  public GrMethodCallInstruction(@NotNull GrNewExpression call, @Nullable DfaValue precalculatedReturnValue) {
    final GroovyResolveResult result = call.advancedResolve();

    myCall = call;
    myNamedArguments = call.getNamedArguments();
    myExpressionArguments = call.getExpressionArguments();
    myClosureArguments = call.getClosureArguments();

    myReturnType = call.getType();
    myTargetMethod = (PsiMethod)result.getElement();

    myShouldFlushFields = !(myReturnType != null && myReturnType.getArrayDimensions() > 0) && !isPureCall(myTargetMethod);
    argumentsToParameters = myTargetMethod instanceof GrAccessorMethod ? null : GrClosureSignatureUtil.mapArgumentsToParameters(
      result, call, false, false, call.getNamedArguments(), myExpressionArguments, myClosureArguments
    );
    myPrecalculatedReturnValue = precalculatedReturnValue;
    myRequiresNotNullQualifier = false;
  }

  public Nullness getParameterNullability(GrExpression e) {
    if (myTargetMethod instanceof GrAccessorMethod) {
      return DfaPsiUtil.getElementNullability(null, ((GrAccessorMethod)myTargetMethod).getProperty());
    }
    final Pair<PsiParameter, PsiType> p = argumentsToParameters == null ? null : argumentsToParameters.get(e);
    final PsiParameter parameter = p == null ? null : p.first;
    final PsiType type = p == null ? null : p.second;
    return DfaPsiUtil.getElementNullability(type, parameter);
  }

  @NotNull
  public PsiElement getCall() {
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

  public boolean requiresNotNullQualifier() {
    return myRequiresNotNullQualifier;
  }

  @Override
  public DfaInstructionState[] acceptGroovy(@NotNull DfaMemoryState state, @NotNull GrInstructionVisitor visitor) {
    return visitor.visitMethodCall(this, state);
  }

  public String toString() {
    return "CALL METHOD " +
           myReturnType +
           " " +
           (myTargetMethod == null ? null : myTargetMethod.getName()) +
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
    return ControlFlowAnalyzer.isPure(myTargetMethod)
           || PropertyUtil.isSimplePropertyGetter(myTargetMethod)
           || GrDfaUtil.isEqualsCallOrIsCall(myTargetMethod);
  }
}
