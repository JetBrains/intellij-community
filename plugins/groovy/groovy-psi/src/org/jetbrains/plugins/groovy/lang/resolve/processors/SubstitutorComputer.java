// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.VolatileNotNullLazyValue;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.PsiClassType.ClassResolveResult;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
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
public class SubstitutorComputer {
  private static final Logger LOG = Logger.getInstance(SubstitutorComputer.class);

  protected final PsiElement myPlace;

  private final PsiType myThisType;
  @Nullable
  private final PsiType[] myArgumentTypes;
  @Nullable
  private final PsiType[] myTypeArguments;
  private final PsiElement myPlaceToInferContext;
  private final NotNullLazyValue<Collection<PsiElement>> myExitPoints;
  private final PsiResolveHelper myHelper;

  public SubstitutorComputer(PsiType thisType,
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

    myHelper = JavaPsiFacade.getInstance(myPlace.getProject()).getResolveHelper();
  }

  @Nullable
  protected PsiType inferContextType() {
    final PsiElement parent = myPlaceToInferContext.getParent();
    if (parent instanceof GrReturnStatement || myExitPoints.getValue().contains(myPlaceToInferContext)) {
      final GrMethod method = PsiTreeUtil.getParentOfType(parent, GrMethod.class, true, GrClosableBlock.class);
      if (method != null) {
        return method.getReturnType();
      }
    }
    else if (parent instanceof GrAssignmentExpression && myPlaceToInferContext.equals(((GrAssignmentExpression)parent).getRValue())) {
      PsiElement lValue = PsiUtil.skipParentheses(((GrAssignmentExpression)parent).getLValue(), false);
      if ((lValue instanceof GrExpression) && !(lValue instanceof GrIndexProperty)) {
        return ((GrExpression)lValue).getNominalType();
      }
      else {
        return null;
      }
    }
    else if (parent instanceof GrVariable) {
      return ((GrVariable)parent).getDeclaredType();
    }
    return null;
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

    Deque<InferenceStep> inferenceQueue = buildInferenceQueue(method, typeParameters, params, argInfos);

    PsiSubstitutor substitutor = partialSubstitutor;
    while (!inferenceQueue.isEmpty()) {
      substitutor = inferenceQueue.pollFirst().doInfer(substitutor);
    }

    for (PsiTypeParameter typeParameter : typeParameters) {
      if (!substitutor.getSubstitutionMap().containsKey(typeParameter)) {
        substitutor = inferFromContext(typeParameter, PsiUtil.getSmartReturnType(method), substitutor);
        if (!substitutor.getSubstitutionMap().containsKey(typeParameter)) {
          substitutor = substitutor.put(typeParameter, null);
        }
      }
    }

    return partialSubstitutor.putAll(substitutor);
  }

  @NotNull
  private Deque<InferenceStep> buildInferenceQueue(@NotNull PsiMethod method,
                                                   @NotNull PsiTypeParameter[] typeParameters,
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
            inferenceQueue.add(handleClosure(paramType, argType, typeParameters));
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
    PsiType[] parameterArray = parameterTypes.toArray(new PsiType[parameterTypes.size()]);
    PsiType[] argumentArray = argumentTypes.toArray(new PsiType[argumentTypes.size()]);
    inferenceQueue.addFirst(buildStep(parameterArray, argumentArray, typeParameters));
    return inferenceQueue;
  }

  InferenceStep buildStep(PsiType[] parameterTypes, PsiType[] argumentTypes, PsiTypeParameter[] typeParameters) {
    return (ps) -> myHelper.inferTypeArguments(collectTypeParams(typeParameters, parameterTypes),
                                               parameterTypes,
                                               argumentTypes,
                                               ps,
                                               LanguageLevel.JDK_1_8);
  }

  private InferenceStep handleClosure(PsiType targetType, PsiType argType, @NotNull PsiTypeParameter[] typeParameters) {
    if (targetType instanceof PsiClassType && TypesUtil.isClassType(targetType, GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
      PsiType[] parameters = ((PsiClassType)targetType).getParameters();
      if (parameters.length != 1) return InferenceStep.EMPTY;
      return buildReturnTypeClosureStep(argType, parameters[0], typeParameters);
    }

    if (isSamConversionAllowed(myPlace)) {
      return handleConversionOfSAMType(targetType, argType, typeParameters);
    }

    return InferenceStep.EMPTY;
  }


  @NotNull
  private InferenceStep handleConversionOfSAMType(@Nullable PsiType targetType,
                                                  @NotNull PsiType closure,
                                                  PsiTypeParameter[] typeParameters) {
    if (!(closure instanceof PsiClassType)) return InferenceStep.EMPTY;
    if (!(targetType instanceof PsiClassType)) return InferenceStep.EMPTY;

    ClassResolveResult resolveResult = ((PsiClassType)targetType).resolveGenerics();

    PsiClass samClass = resolveResult.getElement();
    if (samClass == null) return InferenceStep.EMPTY;

    PsiMethod sam = findSingleAbstractMethod(samClass);
    if (sam == null) return InferenceStep.EMPTY;

    PsiType samReturnType = resolveResult.getSubstitutor().substitute(sam.getReturnType());
    if (samReturnType == null) return InferenceStep.EMPTY;

    return buildReturnTypeClosureStep(closure, samReturnType, typeParameters);
  }

  private InferenceStep buildReturnTypeClosureStep(@NotNull PsiType closure,
                                                   @Nullable PsiType returnType,
                                                   PsiTypeParameter[] typeParameters) {
    PsiType[] parameters = ((PsiClassType)closure).getParameters();
    if (parameters.length != 1) return InferenceStep.EMPTY;
    PsiType[] rightTypes = closure instanceof GrClosureType ? ((GrClosureType)closure).inferParameters() : parameters;

    return buildStep(new PsiType[]{returnType}, rightTypes, typeParameters);
  }

  private static PsiTypeParameter[] collectTypeParams(PsiTypeParameter[] parameters, PsiType[] types) {
    Set<PsiTypeParameter> visited = new HashSet<>();
    collectTypeParams(parameters, visited, types);

    return visited.toArray(new PsiTypeParameter[visited.size()]);
  }

  private static void collectTypeParams(PsiTypeParameter[] parameters,
                                        Set<PsiTypeParameter> visited,
                                        PsiType... type) {
    PsiTypeParameter[] typeParameters = PsiTypesUtil.filterUnusedTypeParameters(parameters, type);
    for (PsiTypeParameter parameter : typeParameters) {
      if (visited.add(parameter)) {
        collectTypeParams(parameters, visited, parameter.getExtendsListTypes());
      }
    }
  }

  private PsiSubstitutor inferFromContext(@NotNull PsiTypeParameter typeParameter,
                                          @Nullable PsiType lType,
                                          @NotNull PsiSubstitutor substitutor) {
    if (myPlace == null) return substitutor;

    final PsiType inferred =
      myHelper.getSubstitutionForTypeParameter(typeParameter, lType, inferContextType(), false, LanguageLevel.JDK_1_8);
    if (inferred != PsiType.NULL) {
      return substitutor.put(typeParameter, inferred);
    }
    return substitutor;
  }

  public PsiType[] getTypeArguments() {
    return myTypeArguments;
  }

  private interface InferenceStep {
    InferenceStep EMPTY = (ps) -> ps;

    PsiSubstitutor doInfer(@NotNull PsiSubstitutor partialSubstitutor);
  }
}
