/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSPREAD_DOT;

/**
 * @author Medvedev Max
 */
public class GrReferenceResolveUtil {
  private GrReferenceResolveUtil() {
  }

  public static boolean resolveImpl(ResolverProcessor processor, GrReferenceExpression place) {
    GrExpression qualifier = place.getQualifier();
    if (qualifier == null) {
      if (!ResolveUtil.treeWalkUp(place, processor, true)) return false;
      if (!processor.hasCandidates()) {
        qualifier = PsiImplUtil.getRuntimeQualifier(place);
        if (qualifier != null) {
          if (!processQualifier(processor, qualifier, place)) return false;
        }
      }
    }
    else {
      if (place.getDotTokenType() == mSPREAD_DOT) {
        if (!processQualifierForSpreadDot(processor, qualifier, place)) return false;
      }
      else {
        if (!processQualifier(processor, qualifier, place)) return false;
      }

      if (qualifier instanceof GrReferenceExpression && "class".equals(((GrReferenceExpression)qualifier).getReferenceName()) ||
          qualifier instanceof GrThisReferenceExpression) {
        if (!processIfJavaLangClass(processor, qualifier.getType(), qualifier, place)) return false;
      }
    }
    return true;
  }

  public static boolean processIfJavaLangClass(ResolverProcessor processor,
                                               PsiType type,
                                               GroovyPsiElement resolveContext,
                                               GroovyPsiElement place) {
    if (!(type instanceof PsiClassType)) return true;

    final PsiClass psiClass = ((PsiClassType)type).resolve();
    if (psiClass == null || !CommonClassNames.JAVA_LANG_CLASS.equals(psiClass.getQualifiedName())) return true;

    final PsiType[] params = ((PsiClassType)type).getParameters();
    if (params.length != 1) return true;

    if (!processClassQualifierType(processor, params[0], resolveContext, place)) return false;
    return true;
  }

  public static boolean processQualifierForSpreadDot(ResolverProcessor processor, GrExpression qualifier, GroovyPsiElement place) {
    PsiType qualifierType = qualifier.getType();


    if (qualifierType instanceof PsiArrayType) {
      if (!processClassQualifierType(processor, ((PsiArrayType)qualifierType).getComponentType(), qualifier, place)) return false;
      return true;
    }


    //process for collections
    if (!(qualifierType instanceof PsiClassType)) return true;

    PsiClassType.ClassResolveResult result = ((PsiClassType)qualifierType).resolveGenerics();
    PsiClass clazz = result.getElement();
    if (clazz == null) return true;

    PsiClass collection = GroovyPsiManager.getInstance(place.getProject())
      .findClassWithCache(CommonClassNames.JAVA_UTIL_COLLECTION, place.getResolveScope());
    if (collection == null || collection.getTypeParameters().length != 1) return true;

    PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(collection, clazz, result.getSubstitutor());
    if (substitutor == null) return true;

    PsiType componentType = substitutor.substitute(collection.getTypeParameters()[0]);
    if (componentType == null) return true;

    if (!processClassQualifierType(processor, componentType, qualifier, place)) return false;
    return true;
  }

  public static boolean processQualifier(PsiScopeProcessor processor, GrExpression qualifier, GroovyPsiElement place) {
    PsiType qualifierType = qualifier.getType();
    if (qualifierType == null) {
      if (qualifier instanceof GrReferenceExpression) {
        PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
        if (resolved instanceof PsiPackage) {
          final ResolveState state = ResolveState.initial().put(ResolverProcessor.RESOLVE_CONTEXT, qualifier);
          if (!resolved.processDeclarations(processor, state, null, place)) return false;
        }
        else {
          qualifierType = TypesUtil.getJavaLangObject(place);
          if (!processClassQualifierType(processor, qualifierType, qualifier, place)) return false;
        }
      }
    }
    else {
      if (qualifierType instanceof PsiIntersectionType) {
        for (PsiType conjunct : ((PsiIntersectionType)qualifierType).getConjuncts()) {
          if (!processClassQualifierType(processor, conjunct, qualifier, place)) return false;
        }
      }
      else {
        if (!processClassQualifierType(processor, qualifierType, qualifier, place)) return false;
        if (qualifier instanceof GrReferenceExpression) {
          PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
          if (resolved instanceof PsiClass) { //omitted .class
            PsiClass javaLangClass = PsiUtil.getJavaLangClass(resolved, place.getResolveScope());
            if (javaLangClass != null) {
              ResolveState state = ResolveState.initial();
              PsiTypeParameter[] typeParameters = javaLangClass.getTypeParameters();
              PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
              if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;
              if (typeParameters.length == 1) {
                substitutor = substitutor.put(typeParameters[0], qualifierType);
                state = state.put(PsiSubstitutor.KEY, substitutor);
              }
              if (!javaLangClass.processDeclarations(processor, state, null, place)) return false;
              PsiType javaLangClassType =
                JavaPsiFacade.getInstance(place.getProject()).getElementFactory().createType(javaLangClass, substitutor);
              if (!ResolveUtil.processNonCodeMembers(javaLangClassType, processor, place, state)) return false;
            }
          }
        }
      }
    }
    return true;
  }

  public static boolean processClassQualifierType(PsiScopeProcessor processor,
                                                  PsiType qualifierType,
                                                  GroovyPsiElement resolveContext,
                                                  GroovyPsiElement place) {
    final ResolveState state;
    if (qualifierType instanceof PsiClassType) {
      PsiClassType.ClassResolveResult qualifierResult = ((PsiClassType)qualifierType).resolveGenerics();
      PsiClass qualifierClass = qualifierResult.getElement();
      state = ResolveState.initial().put(PsiSubstitutor.KEY, qualifierResult.getSubstitutor())
        .put(ResolverProcessor.RESOLVE_CONTEXT, resolveContext);
      if (qualifierClass != null) {
        if (!qualifierClass.processDeclarations(processor, state, null, place)) return false;
      }
    }
    else if (qualifierType instanceof PsiArrayType) {
      final GrTypeDefinition arrayClass = GroovyPsiManager.getInstance(place.getProject()).getArrayClass();
      state = ResolveState.initial();
      if (!arrayClass.processDeclarations(processor, state, null, place)) return false;
    }
    else if (qualifierType instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)qualifierType).getConjuncts()) {
        if (!processClassQualifierType(processor, conjunct, resolveContext, place)) return false;
      }
      return true;
    }
    else {
      state = ResolveState.initial();
    }

    if (!ResolveUtil.processCategoryMembers(place, processor)) return false;
    if (!ResolveUtil.processNonCodeMembers(qualifierType, processor, place, state)) return false;
    return true;
  }
}
