/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.hash.HashSet;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Set;

/**
 * @author Max Medvedev
 */
public class SubstitutorComputer {
  private static final Logger LOG = Logger.getInstance(SubstitutorComputer.class);

  protected final PsiElement myPlace;

  private final PsiType myThisType;
  @Nullable private final PsiType[] myArgumentTypes;
  private final PsiType[] myTypeArguments;

  private final boolean myAllVariants;

  private final GrControlFlowOwner myFlowOwner;
  private final PsiElement myPlaceToInferContext;


  public SubstitutorComputer(PsiType thisType,
                             @Nullable PsiType[] argumentTypes,
                             PsiType[] typeArguments,
                             boolean allVariants,
                             PsiElement place,
                             PsiElement placeToInferContext) {
    myThisType = thisType;
    myArgumentTypes = argumentTypes;
    myTypeArguments = typeArguments;
    myAllVariants = allVariants;
    myPlace = place;
    myPlaceToInferContext = placeToInferContext;

    if (canBeExitPoint(place)) {
      myFlowOwner = ControlFlowUtils.findControlFlowOwner(place);
    }
    else {
      myFlowOwner = null;
    }
  }

  @Nullable
  protected PsiType inferContextType() {
    final PsiElement parent = myPlaceToInferContext.getParent();
    if (parent instanceof GrReturnStatement || exitsContains(myPlaceToInferContext)) {
      final GrMethod method = PsiTreeUtil.getParentOfType(parent, GrMethod.class, true, GrClosableBlock.class);
      if (method != null) {
        return method.getReturnType();
      }
    }
    else if (parent instanceof GrAssignmentExpression && myPlaceToInferContext.equals(((GrAssignmentExpression)parent).getRValue())) {
      return ((GrAssignmentExpression)parent).getLValue().getType();
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

  public PsiSubstitutor obtainSubstitutor(PsiSubstitutor substitutor, PsiMethod method, ResolveState state) {
    final PsiTypeParameter[] typeParameters = method.getTypeParameters();
    if (myTypeArguments.length == typeParameters.length) {
      for (int i = 0; i < typeParameters.length; i++) {
        PsiTypeParameter typeParameter = typeParameters[i];
        final PsiType typeArgument = myTypeArguments[i];
        substitutor = substitutor.put(typeParameter, typeArgument);
      }
      return substitutor;
    }

    if (myArgumentTypes != null && method.hasTypeParameters()) {
      PsiType[] argTypes = myArgumentTypes;
      final PsiElement resolveContext = state.get(ResolverProcessor.RESOLVE_CONTEXT);
      if (method instanceof GrGdkMethod) {
        //type inference should be performed from static method
        PsiType[] newArgTypes = new PsiType[argTypes.length + 1];
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

  private PsiSubstitutor inferMethodTypeParameters(PsiMethod method,
                                                   PsiSubstitutor partialSubstitutor,
                                                   final PsiTypeParameter[] typeParameters,
                                                   final PsiType[] argTypes) {
    if (typeParameters.length == 0 || myArgumentTypes == null) return partialSubstitutor;

    final GrClosureSignature erasedSignature = GrClosureSignatureUtil.createSignatureWithErasedParameterTypes(method);

    final GrClosureSignature signature = GrClosureSignatureUtil.createSignature(method, partialSubstitutor);
    final GrClosureParameter[] params = signature.getParameters();

    final GrClosureSignatureUtil.ArgInfo<PsiType>[] argInfos =
      GrClosureSignatureUtil.mapArgTypesToParameters(erasedSignature, argTypes, myPlace, myAllVariants);
    if (argInfos == null) return partialSubstitutor;

    int max = Math.max(params.length, argTypes.length);

    PsiType[] parameterTypes = new PsiType[max];
    PsiType[] argumentTypes = new PsiType[max];
    int i = 0;
    for (int paramIndex = 0; paramIndex < argInfos.length; paramIndex++) {
      PsiType paramType = params[paramIndex].getType();

      GrClosureSignatureUtil.ArgInfo<PsiType> argInfo = argInfos[paramIndex];
      if (argInfo != null) {
        if (argInfo.isMultiArg) {
          if (paramType instanceof PsiArrayType) paramType = ((PsiArrayType)paramType).getComponentType();
        }
        for (PsiType type : argInfo.args) {
          argumentTypes[i] = handleConversion(paramType, type);
          parameterTypes[i] = paramType;
          i++;
        }
      }
      else {
        parameterTypes[i] = paramType;
        argumentTypes[i] = PsiType.NULL;
        i++;
      }
    }
    final PsiResolveHelper helper = JavaPsiFacade.getInstance(method.getProject()).getResolveHelper();
    PsiSubstitutor substitutor = helper.inferTypeArguments(typeParameters, parameterTypes, argumentTypes, LanguageLevel.JDK_1_7);
    for (PsiTypeParameter typeParameter : typeParameters) {
      if (!substitutor.getSubstitutionMap().containsKey(typeParameter)) {
        substitutor = inferFromContext(typeParameter, PsiUtil.getSmartReturnType(method), substitutor, helper);
        if (!substitutor.getSubstitutionMap().containsKey(typeParameter)) {
          substitutor = substitutor.put(typeParameter, null);
        }
      }
    }

    return partialSubstitutor.putAll(substitutor);
  }

  private PsiType handleConversion(PsiType paramType, PsiType argType) {
    if (!TypesUtil.isAssignable(TypeConversionUtil.erasure(paramType), argType, myPlace) &&
        TypesUtil.isAssignableByMethodCallConversion(paramType, argType, myPlace)) {
      return paramType;
    }
    return argType;
  }

  private PsiSubstitutor inferFromContext(PsiTypeParameter typeParameter,
                                          PsiType lType,
                                          PsiSubstitutor substitutor,
                                          PsiResolveHelper helper) {
    if (myPlace == null) return substitutor;

    final PsiType inferred = helper.getSubstitutionForTypeParameter(typeParameter, lType, inferContextType(), false, LanguageLevel.JDK_1_7);
    if (inferred != PsiType.NULL) {
      return substitutor.put(typeParameter, inferred);
    }
    return substitutor;
  }

  private Set<PsiElement> myExitPoints;
  protected boolean exitsContains(PsiElement place) {
    if (myFlowOwner == null) return false;
    if (myExitPoints == null) {
      myExitPoints = new HashSet<PsiElement>();
      myExitPoints.addAll(ControlFlowUtils.collectReturns(myFlowOwner));
    }
    return myExitPoints.contains(place);
  }

  public PsiType[] getTypeArguments() {
    return myTypeArguments;
  }
}
