// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.VolatileNotNullLazyValue;
import com.intellij.psi.*;
import com.intellij.psi.PsiClassType.ClassResolveResult;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.emptyList;
import static com.intellij.util.containers.ContainerUtil.newHashSet;
import static org.jetbrains.plugins.groovy.lang.sam.SamConversionKt.findSingleAbstractMethod;
import static org.jetbrains.plugins.groovy.lang.sam.SamConversionKt.isSamConversionAllowed;

/**
 * @author Max Medvedev
 */
@SuppressWarnings("Duplicates")
public class SubstitutorComputer2 {
  private static final Logger LOG = Logger.getInstance(SubstitutorComputer2.class);

  protected final PsiElement myPlace;

  private final PsiType myThisType;
  @Nullable
  private final PsiType[] myArgumentTypes;
  @Nullable
  private final PsiType[] myTypeArguments;
  private final PsiElement myPlaceToInferContext;
  private final NotNullLazyValue<Collection<PsiElement>> myExitPoints;


  public SubstitutorComputer2(PsiType thisType,
                              @Nullable PsiType[] argumentTypes,
                              @Nullable PsiType[] typeArguments,
                              PsiElement place,
                              PsiElement placeToInferContext) {
    myThisType = thisType;
    myArgumentTypes = argumentTypes;
    myTypeArguments = typeArguments;
    myPlace = place;
    myPlaceToInferContext = placeToInferContext;
    myExitPoints = VolatileNotNullLazyValue.createValue(() -> {
      if (canBeExitPoint(place)) {
        GrControlFlowOwner flowOwner = ControlFlowUtils.findControlFlowOwner(place);
        return newHashSet(ControlFlowUtils.collectReturns(flowOwner));
      }
      else {
        return emptyList();
      }
    });

  }

  @NotNull
  protected InferenceStep buildReturnStep(@NotNull PsiMethod method) {
    PsiType smartReturnType = PsiUtil.getSmartReturnType(method);
    final PsiElement parent = myPlaceToInferContext.getParent();
    final GrMethod parentMethod = PsiTreeUtil.getParentOfType(parent, GrMethod.class, true, GrClosableBlock.class);
    PsiType leftType = null;
    if (parent instanceof GrReturnStatement && parentMethod!= null) {
      leftType = parentMethod.getReturnType();
    } else if(myExitPoints.getValue().contains(myPlaceToInferContext) && parentMethod!= null) {
        PsiType returnType = parentMethod.getReturnType();
        if(TypeConversionUtil.isVoidType(returnType) || TypeConversionUtil.isVoidType(smartReturnType)) return InferenceStep.EMPTY;
        leftType = returnType;

    } else if (parent instanceof GrAssignmentExpression && myPlaceToInferContext.equals(((GrAssignmentExpression)parent).getRValue())) {
      PsiElement lValue = PsiUtil.skipParentheses(((GrAssignmentExpression)parent).getLValue(), false);
      if ((lValue instanceof GrExpression) && !(lValue instanceof GrIndexProperty)) {
        leftType = ((GrExpression)lValue).getNominalType();
      }
      else {
        return InferenceStep.EMPTY;
      }
    }
    else if (parent instanceof GrVariable) {
      leftType = ((GrVariable)parent).getDeclaredType();
    }

    return buildStep(leftType, smartReturnType);
  }

  private static boolean canBeExitPoint(PsiElement place) {
    while (place != null) {
      if (place instanceof GrMethod || place instanceof GrClosableBlock || place instanceof GrClassInitializer) return true;
      if (place instanceof GrThrowStatement || place instanceof GrTypeDefinitionBody || place instanceof GroovyFile) return false;
      place = place.getParent();
    }
    return false;
  }

  public PsiSubstitutor obtainSubstitutor(@NotNull PsiSubstitutor substitutor,
                                          @NotNull PsiMethod method,
                                          @Nullable PsiElement resolveContext) {
    final PsiTypeParameter[] typeParameters = method.getTypeParameters();
    if (myTypeArguments != null && myTypeArguments.length == typeParameters.length) {
      for (int i = 0; i < typeParameters.length; i++) {
        PsiTypeParameter typeParameter = typeParameters[i];
        final PsiType typeArgument = myTypeArguments[i];
        substitutor = substitutor.put(typeParameter, typeArgument);
      }
      return substitutor;
    }

    if (myArgumentTypes != null && method.hasTypeParameters()) {
      PsiType[] argTypes = myArgumentTypes;
      if (method instanceof GrGdkMethod) {
        //type inference should be performed from static method
        PsiType[] newArgTypes = PsiType.createArray(argTypes.length + 1);
        if (GdkMethodUtil.isInWithContext(resolveContext)) {
          newArgTypes[0] = ((GrExpression)resolveContext).getType();
        }
        else {
          newArgTypes[0] = myThisType;
        }
        System.arraycopy(argTypes, 0, newArgTypes, 1, argTypes.length);
        argTypes = newArgTypes;

        method = ((GrGdkMethod)method).getStaticMethod();
        LOG.assertTrue(method.isValid());
      }

      return inferMethodTypeParameters(method, substitutor, typeParameters, argTypes);
    }

    return substitutor;
  }

  private PsiSubstitutor inferMethodTypeParameters(@NotNull PsiMethod method,
                                                   @NotNull PsiSubstitutor partialSubstitutor,
                                                   @NotNull PsiTypeParameter[] typeParameters,
                                                   @NotNull PsiType[] argTypes) {
    if (typeParameters.length == 0 || myArgumentTypes == null) return partialSubstitutor;

    final GrClosureSignature erasedSignature = GrClosureSignatureUtil.createSignature(method, partialSubstitutor, true);

    final GrClosureSignature signature = GrClosureSignatureUtil.createSignature(method, partialSubstitutor);
    final GrClosureParameter[] params = signature.getParameters();

    final GrClosureSignatureUtil.ArgInfo<PsiType>[] argInfos =
      GrClosureSignatureUtil.mapArgTypesToParameters(erasedSignature, argTypes, myPlace, true);

    if (argInfos == null || params.length > argInfos.length) return partialSubstitutor;

    Deque<InferenceStep> inferenceQueue = buildInferenceQueue(method, params, argInfos);

    GroovyInferenceSession session = new GroovyInferenceSession(typeParameters, partialSubstitutor, method.getManager(), myPlace);
    while (!inferenceQueue.isEmpty()) {
      if(!inferenceQueue.pollFirst().doInfer(session)) {
        return partialSubstitutor;
      }
    }

    return partialSubstitutor.putAll(session.result());
  }

  @NotNull
  private Deque<InferenceStep> buildInferenceQueue(@NotNull PsiMethod method,
                                                   GrClosureParameter[] params,
                                                   GrClosureSignatureUtil.ArgInfo<PsiType>[] argInfos) {
    Deque<InferenceStep> inferenceQueue = new ArrayDeque<>();

    List<PsiType> parameterTypes = new ArrayList<>();
    List<PsiType> argumentTypes = new ArrayList<>();
    for (int paramIndex = 0; paramIndex < params.length; paramIndex++) {
      PsiType paramType = params[paramIndex].getType();

      GrClosureSignatureUtil.ArgInfo<PsiType> argInfo = argInfos[paramIndex];
      if (argInfo != null) {
        if (argInfo.isMultiArg) {
          if (paramType instanceof PsiArrayType) paramType = ((PsiArrayType)paramType).getComponentType();
        }
        for (PsiType type : argInfo.args) {
          PsiType argType = type;
          if (InheritanceUtil.isInheritor(argType, GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
            inferenceQueue.add(handleClosure(paramType, argType));
            continue;
          }


          if (argType instanceof GrTupleType) {
            PsiType rawWildcardType = TypesUtil.rawWildcard(argType, method);
            argType = rawWildcardType != null ? rawWildcardType : argType;
          }

          if (argType != null) {
            argType = com.intellij.psi.util.PsiUtil.captureToplevelWildcards(argType, method);
          }
          parameterTypes.add(paramType);
          argumentTypes.add(argType);
        }
      }
      else {
        parameterTypes.add(paramType);
        argumentTypes.add(PsiType.NULL);
      }
    }

    inferenceQueue.addFirst(buildReturnStep(method));
    PsiType[] parameterArray = parameterTypes.toArray(PsiType.EMPTY_ARRAY);
    PsiType[] argumentArray = argumentTypes.toArray(PsiType.EMPTY_ARRAY);
    inferenceQueue.addFirst(buildStep(parameterArray, argumentArray));
    return inferenceQueue;
  }

  InferenceStep buildStep(PsiType[] parameterTypes, PsiType[] argumentTypes) {
    return (session) -> {
      for (int i = 0; i < argumentTypes.length; i++) {

        PsiType leftType = parameterTypes[i];
        PsiType rightType = argumentTypes[i];
        if (leftType != null && rightType != null) {
          session.addConstraint(leftType, rightType);
        }
      }

      return session.doInfer();
    };
  }

  InferenceStep buildStep(PsiType leftType, PsiType rightType) {
    return (session) -> {
      if (leftType != null && rightType != null) {
        session.addConstraint(leftType, rightType);
      }
      return session.doInfer();
    };
  }

  private InferenceStep handleClosure(PsiType targetType, PsiType argType) {
    if (targetType instanceof PsiClassType && TypesUtil.isClassType(targetType, GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
      PsiType[] parameters = ((PsiClassType)targetType).getParameters();
      if (parameters.length != 1) return InferenceStep.EMPTY;
      return buildReturnTypeClosureStep(argType, parameters[0]);
    }

    if (isSamConversionAllowed(myPlace)) {
      return handleConversionOfSAMType(targetType, argType);
    }

    return InferenceStep.EMPTY;
  }


  @NotNull
  private InferenceStep handleConversionOfSAMType(@Nullable PsiType targetType,
                                                  @NotNull PsiType closure) {
    if (!(closure instanceof PsiClassType)) return InferenceStep.EMPTY;
    if (!(targetType instanceof PsiClassType)) return InferenceStep.EMPTY;

    ClassResolveResult resolveResult = ((PsiClassType)targetType).resolveGenerics();

    PsiClass samClass = resolveResult.getElement();
    if (samClass == null) return InferenceStep.EMPTY;

    PsiMethod sam = findSingleAbstractMethod(samClass);
    if (sam == null) return InferenceStep.EMPTY;

    PsiType samReturnType = resolveResult.getSubstitutor().substitute(sam.getReturnType());
    if (samReturnType == null) return InferenceStep.EMPTY;

    return buildReturnTypeClosureStep(closure, samReturnType);
  }

  private InferenceStep buildReturnTypeClosureStep(@NotNull PsiType closure,
                                                   @Nullable PsiType returnType) {
    PsiType[] parameters = ((PsiClassType)closure).getParameters();
    if (parameters.length != 1) return InferenceStep.EMPTY;
    PsiType[] rightTypes = closure instanceof GrClosureType ? ((GrClosureType)closure).inferParameters() : parameters;

    return buildStep(new PsiType[]{returnType}, rightTypes);
  }


  public PsiType[] getTypeArguments() {
    return myTypeArguments;
  }

  private interface InferenceStep {
    InferenceStep EMPTY = (s) -> true;

    boolean doInfer(@NotNull GroovyInferenceSession session);
  }
}
