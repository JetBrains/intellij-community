/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.util.*;
import com.intellij.util.containers.hash.HashSet;
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
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureToSamConverter;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
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

  private final GrControlFlowOwner myFlowOwner;
  private final PsiElement myPlaceToInferContext;
  private final PsiResolveHelper myHelper;


  public SubstitutorComputer(PsiType thisType,
                             @Nullable PsiType[] argumentTypes,
                             PsiType[] typeArguments,
                             PsiElement place,
                             PsiElement placeToInferContext) {
    myThisType = thisType;
    myArgumentTypes = argumentTypes;
    myTypeArguments = typeArguments;
    myPlace = place;
    myPlaceToInferContext = placeToInferContext;

    if (canBeExitPoint(place)) {
      myFlowOwner = ControlFlowUtils.findControlFlowOwner(place);
    }
    else {
      myFlowOwner = null;
    }

    myHelper = JavaPsiFacade.getInstance(myPlace.getProject()).getResolveHelper();

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
      PsiElement lValue = PsiUtil.skipParentheses(((GrAssignmentExpression)parent).getLValue(), false);
      if ((lValue instanceof GrExpression) && !(lValue instanceof GrIndexProperty)) {
        return ((GrExpression)lValue).getType();
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

    final GrClosureSignatureUtil.ArgInfo<PsiType>[] argInfos = GrClosureSignatureUtil.mapArgTypesToParameters(erasedSignature, argTypes, myPlace, true);
    if (argInfos == null) return partialSubstitutor;

    int max = Math.max(params.length, argTypes.length);

    PsiType[] parameterTypes = PsiType.createArray(max);
    PsiType[] argumentTypes = PsiType.createArray(max);
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
    PsiSubstitutor substitutor = myHelper.inferTypeArguments(typeParameters, parameterTypes, argumentTypes, LanguageLevel.JDK_1_7);
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

  @Nullable
  private PsiType handleConversion(@Nullable PsiType paramType, @Nullable PsiType argType) {
    if (ClosureToSamConverter.isSamConversionAllowed(myPlace) &&
        InheritanceUtil.isInheritor(argType, GroovyCommonClassNames.GROOVY_LANG_CLOSURE) &&
        !TypesUtil.isClassType(paramType, GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
      PsiType converted = handleConversionOfSAMType(paramType, (PsiClassType)argType);
      if (converted != null) {
        return converted;
      }

      return argType;
    }

    if (!TypesUtil.isAssignable(TypeConversionUtil.erasure(paramType), argType, myPlace) &&
        TypesUtil.isAssignableByMethodCallConversion(paramType, argType, myPlace)) {
      return paramType;
    }
    return argType;
  }

  @Nullable
  private PsiType handleConversionOfSAMType(@Nullable PsiType samType, @NotNull PsiClassType closure) {
    if (samType instanceof PsiClassType) {
      PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)samType).resolveGenerics();
      PsiClass samClass = resolveResult.getElement();

      if (samClass != null && samClass.getTypeParameters().length != 0) {
        MethodSignature samSignature = ClosureToSamConverter.findSingleAbstractMethod(samClass, PsiSubstitutor.EMPTY);
        if (samSignature != null) {

          PsiMethod samMethod = MethodSignatureUtil.findMethodBySignature(samClass, samSignature, true);
          if (samMethod != null) {
            PsiType[] closureArgs = closure.getParameters();
            if (closureArgs.length == 1 && samMethod.getReturnType() != null) {
              PsiSubstitutor substitutor = myHelper.inferTypeArguments(samClass.getTypeParameters(),
                                                                       new PsiType[]{samMethod.getReturnType()},
                                                                       closureArgs,
                                                                       LanguageLevel.JDK_1_7);

              if (!substitutor.getSubstitutionMap().isEmpty()) {
                return JavaPsiFacade.getElementFactory(myPlace.getProject()).createType(samClass, substitutor);
              }
            }
          }
        }
      }
    }

    return null;
  }


  private PsiSubstitutor inferFromContext(@NotNull PsiTypeParameter typeParameter,
                                          @Nullable PsiType lType,
                                          @NotNull PsiSubstitutor substitutor) {
    if (myPlace == null) return substitutor;

    final PsiType inferred = myHelper.getSubstitutionForTypeParameter(typeParameter, lType, inferContextType(), false, LanguageLevel.JDK_1_7);
    if (inferred != PsiType.NULL) {
      return substitutor.put(typeParameter, inferred);
    }
    return substitutor;
  }

  private Set<PsiElement> myExitPoints;
  protected boolean exitsContains(PsiElement place) {
    if (myFlowOwner == null) return false;
    if (myExitPoints == null) {
      myExitPoints = new HashSet<>();
      myExitPoints.addAll(ControlFlowUtils.collectReturns(myFlowOwner));
    }
    return myExitPoints.contains(place);
  }

  public PsiType[] getTypeArguments() {
    return myTypeArguments;
  }
}
