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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTraitType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParameterEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ClosureMissingMethodContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolverProcessor;

/**
 * @author Medvedev Max
 */
public class GrReferenceResolveRunner {
  private final GrReferenceExpression place;
  private final GroovyResolverProcessor processor;

  public GrReferenceResolveRunner(GrReferenceExpression place,
                                  GroovyResolverProcessor processor) {
    this.place = place;
    this.processor = processor;
  }

  public void resolveImpl() {
    GrExpression qualifier = place.getQualifier();
    if (qualifier == null) {
      if (!ResolveUtil.treeWalkUp(place, processor, true)) return;

      if (place.getContext() instanceof GrMethodCall) {
        if (!ClosureMissingMethodContributor.processMethodsFromClosures(place, processor)) return;
      }

      final GrExpression runtimeQualifier = PsiImplUtil.getRuntimeQualifier(place);
      if (runtimeQualifier != null) {
        processQualifier(runtimeQualifier);
      }
    }
    else {
      if (place.getDotTokenType() == GroovyTokenTypes.mSPREAD_DOT) {
        final PsiType qtype = qualifier.getType();
        final PsiType componentType = ClosureParameterEnhancer.findTypeForIteration(qtype, place);
        if (componentType != null) {
          final ResolveState state = ResolveState.initial()
            .put(ClassHint.RESOLVE_CONTEXT, qualifier)
            .put(SpreadState.SPREAD_STATE, SpreadState.create(qtype, null));
          processQualifierType(componentType, state);
        }
      }
      else {
        if (ResolveUtil.isClassReference(place)) return;
        if (!processJavaLangClass(qualifier)) return;
        processQualifier(qualifier);
      }
    }
  }

  private boolean processJavaLangClass(@NotNull GrExpression qualifier) {
    if (!(qualifier instanceof GrReferenceExpression)) return true;

    //optimization: only 'class' or 'this' in static context can be an alias of java.lang.Class
    if (!"class".equals(((GrReferenceExpression)qualifier).getReferenceName()) &&
        !PsiUtil.isThisReference(qualifier) &&
        !(((GrReferenceExpression)qualifier).resolve() instanceof PsiClass)) {
      return true;
    }

    PsiType classType = ResolveUtil.unwrapClassType(qualifier.getType());
    return classType == null || processQualifierType(classType, ResolveState.initial().put(ClassHint.RESOLVE_CONTEXT, qualifier));
  }

  private boolean processQualifier(@NotNull GrExpression qualifier) {
    PsiType qualifierType = qualifier.getType();
    ResolveState state = ResolveState.initial().put(ClassHint.RESOLVE_CONTEXT, qualifier);
    if (qualifierType == null || PsiType.VOID.equals(qualifierType)) {
      if (qualifier instanceof GrReferenceExpression) {
        PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
        if (resolved instanceof PsiClass) {
          if (!ResolveUtil.processClassDeclarations((PsiClass)resolved, processor, state, null, place)) return false;
        }
        else if (resolved != null && !resolved.processDeclarations(processor, state, null, place)) return false;
        if (!(resolved instanceof PsiPackage)) {
          PsiType objectQualifier = TypesUtil.getJavaLangObject(place);
          if (!processQualifierType(objectQualifier, state)) return false;
        }
      }
    }
    else {
      if (!processQualifierType(qualifierType, state)) return false;
    }
    return true;
  }

  private boolean processQualifierType(@NotNull PsiType originalQualifierType,
                                       @NotNull ResolveState state) {
    PsiType qualifierType = originalQualifierType instanceof PsiDisjunctionType
                            ? ((PsiDisjunctionType)originalQualifierType).getLeastUpperBound()
                            : originalQualifierType;

    if (qualifierType instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)qualifierType).getConjuncts()) {
        if (!processQualifierType(conjunct, state)) return false;
      }
      return true;
    }

    if (qualifierType instanceof PsiCapturedWildcardType) {
      PsiWildcardType wildcard = ((PsiCapturedWildcardType)qualifierType).getWildcard();
      if (wildcard.isExtends()) {
        PsiType bound = wildcard.getExtendsBound();
        return processQualifierType(bound, state);
      }
    }

    if (qualifierType instanceof GrTraitType) {
      return processTraitType((GrTraitType)qualifierType, state);
    }

    if (qualifierType instanceof PsiClassType) {
      PsiClassType.ClassResolveResult qualifierResult = ((PsiClassType)qualifierType).resolveGenerics();
      PsiClass qualifierClass = qualifierResult.getElement();
      if (qualifierClass != null) {
        if (!ResolveUtil.processClassDeclarations(qualifierClass, processor, state.put(PsiSubstitutor.KEY, qualifierResult.getSubstitutor()), null, place)) {
          return false;
        }
      }
    }
    else if (qualifierType instanceof PsiArrayType) {
      final GroovyPsiManager gmanager = GroovyPsiManager.getInstance(place.getProject());
      final GrTypeDefinition arrayClass = gmanager.getArrayClass(((PsiArrayType)qualifierType).getComponentType());
      if (arrayClass != null && !ResolveUtil.processClassDeclarations(arrayClass, processor, state, null, place)) return false;
    }

    if (!(place.getParent() instanceof GrMethodCall) && InheritanceUtil.isInheritor(qualifierType, CommonClassNames.JAVA_UTIL_COLLECTION)) {
      final PsiType componentType = ClosureParameterEnhancer.findTypeForIteration(qualifierType, place);
      if (componentType != null) {
        final SpreadState spreadState = state.get(SpreadState.SPREAD_STATE);
        processQualifierType(componentType, state.put(SpreadState.SPREAD_STATE, SpreadState.create(qualifierType, spreadState)));
      }
    }

    if (!ResolveUtil.processCategoryMembers(place, processor, state)) return false;
    if (!ResolveUtil.processNonCodeMembers(qualifierType, processor, place, state)) return false;
    return true;
  }

  /**
   * Process trait type conjuncts in reversed order because last applied trait matters.
   */
  private boolean processTraitType(@NotNull GrTraitType traitType, @NotNull ResolveState state) {
    final PsiType[] conjuncts = traitType.getConjuncts();
    for (int i = conjuncts.length - 1; i >= 0; i--) {
      if (!processQualifierType(conjuncts[i], state)) return false;
    }
    return true;
  }
}
