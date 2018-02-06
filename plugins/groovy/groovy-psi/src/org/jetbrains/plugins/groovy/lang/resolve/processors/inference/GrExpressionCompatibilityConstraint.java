// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.InputOutputConstraintFormula;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

@SuppressWarnings("Duplicates")
public class GrExpressionCompatibilityConstraint extends InputOutputConstraintFormula {
  private final PsiExpression myExpression;
  private PsiType myT;

  public GrExpressionCompatibilityConstraint(@NotNull PsiExpression expression, @NotNull PsiType type) {
    myExpression = expression;
    myT = type;
  }

  @Override
  public boolean reduce(InferenceSession session, List<ConstraintFormula> constraints) {
    if (myExpression instanceof PsiCall) {
      final InferenceSession callSession = reduceExpressionCompatibilityConstraint(session, myExpression, myT, true);
      if (callSession == null) {
        return false;
      }
      if (callSession != session) {
        session.getInferenceSessionContainer().registerNestedSession(callSession);
        session.propagateVariables(callSession.getInferenceVariables(), callSession.getRestoreNameSubstitution());
        for (Pair<InferenceVariable[], PsiClassType> pair : callSession.myIncorporationPhase.getCaptures()) {
          session.myIncorporationPhase.addCapture(pair.first, pair.second);
        }
        callSession.setUncheckedInContext();
      }
      return true;
    }




    return true;
  }

  public static InferenceSession reduceExpressionCompatibilityConstraint(InferenceSession session,
                                                                         PsiExpression expression,
                                                                         PsiType targetType,
                                                                         boolean registerErrorOnFailure) {
    if (!PsiPolyExpressionUtil.isPolyExpression(expression)) {
      return session;
    }
    final PsiExpressionList argumentList = ((PsiCall)expression).getArgumentList();
    if (argumentList != null) {
      final MethodCandidateInfo.CurrentCandidateProperties candidateProperties = MethodCandidateInfo.getCurrentMethod(argumentList);
      PsiType returnType = null;
      PsiTypeParameter[] typeParams = null;
      final JavaResolveResult resolveResult = candidateProperties != null ? null : PsiDiamondType
        .getDiamondsAwareResolveResult((PsiCall)expression);
      final PsiMethod method = InferenceSession.getCalledMethod((PsiCall)expression);

      if (method != null && !method.isConstructor()) {
        returnType = method.getReturnType();
        typeParams = method.getTypeParameters();
      }
      else if (resolveResult != null) {
        final PsiClass psiClass = method != null ? method.getContainingClass() : (PsiClass)resolveResult.getElement();
        if (psiClass != null) {
          returnType = JavaPsiFacade.getElementFactory(argumentList.getProject()).createType(psiClass, PsiSubstitutor.EMPTY);
          typeParams = psiClass.getTypeParameters();
          if (method != null && method.hasTypeParameters()) {
            typeParams = ArrayUtil.mergeArrays(typeParams, method.getTypeParameters());
          }
        }
      }
      else {
        return session;
      }

      if (typeParams != null) {
        PsiSubstitutor siteSubstitutor = InferenceSession.chooseSiteSubstitutor(candidateProperties, resolveResult, method);
        final InferenceSession callSession = new InferenceSession(typeParams, siteSubstitutor, expression.getManager(), expression);
        callSession.propagateVariables(session.getInferenceVariables(), session.getRestoreNameSubstitution());
        if (method != null) {
          final PsiExpression[] args = argumentList.getExpressions();
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          callSession.initExpressionConstraints(parameters, args, expression, method, InferenceSession
            .chooseVarargsMode(candidateProperties, resolveResult));
        }
        if (callSession.repeatInferencePhases()) {

          if (PsiType.VOID.equals(targetType)) {
            return callSession;
          }

          if (returnType != null) {
            callSession.registerReturnTypeConstraints(siteSubstitutor.substitute(returnType), targetType, expression);
          }
          if (callSession.repeatInferencePhases()) {
            return callSession;
          }
        }

        //copy incompatible message if any
        final List<String> messages = callSession.getIncompatibleErrorMessages();
        if (messages != null) {
          for (String message : messages) {
            session.registerIncompatibleErrorMessage(message);
          }
        }
        return null;
      }
      else if (registerErrorOnFailure) {
        session.registerIncompatibleErrorMessage("Failed to resolve argument");
        return null;
      }
    }
    return session;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GrExpressionCompatibilityConstraint that = (GrExpressionCompatibilityConstraint)o;

    if (!myExpression.equals(that.myExpression)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myExpression.hashCode();
  }

  @Override
  public PsiExpression getExpression() {
    return myExpression;
  }

  @Override
  public PsiType getT() {
    return myT;
  }

  @Override
  protected void setT(PsiType t) {
    myT = t;
  }

  @Override
  protected InputOutputConstraintFormula createSelfConstraint(PsiType type, PsiExpression expression) {
    return new GrExpressionCompatibilityConstraint(expression, type);
  }

  @Override
  protected void collectReturnTypeVariables(InferenceSession session,
                                            PsiExpression psiExpression,
                                            PsiType returnType,
                                            Set<InferenceVariable> result) {

  }
}
