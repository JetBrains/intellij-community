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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.CompletionProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSPREAD_DOT;

/**
 * @author Medvedev Max
 */
public class GrReferenceResolveUtil {

  private GrReferenceResolveUtil() {
  }

  static boolean resolveImpl(ResolverProcessor processor, GrReferenceExpression place) {
    GrExpression qualifier = place.getQualifier();
    if (qualifier == null) {
      if (processor instanceof MethodResolverProcessor || processor instanceof CompletionProcessor) {
        processStaticImports(place.getContainingFile(), processor, ResolveState.initial(), place);
        if (processor instanceof MethodResolverProcessor && ((MethodResolverProcessor)processor).hasApplicableCandidates()) {
          return false;
        }
      }

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
        final PsiType qtype = qualifier.getType();
        final PsiType componentType = getComponentTypeForSpreadDot(qtype, place);
        if (componentType != null) {
          final ResolveState state = ResolveState.initial()
            .put(ResolverProcessor.RESOLVE_CONTEXT, qualifier)
            .put(SpreadState.SPREAD_STATE, SpreadState.create(qtype, null));
          if (!processQualifierType(processor, componentType, state, place)) return false;
        }
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

  private static boolean processStaticImports(PsiFile file, ResolverProcessor processor, ResolveState state, PsiElement place) {
    if (file instanceof GroovyFile) {
      GrImportStatement[] imports = ((GroovyFile)file).getImportStatements();
      for (GrImportStatement anImport : imports) {
        if (anImport.isStatic()) {
          if (!anImport.processDeclarations(processor, state, null, place)) return false;
        }
      }
    }
    return true;
  }

  private static boolean processIfJavaLangClass(ResolverProcessor processor,
                                               PsiType type,
                                               GroovyPsiElement resolveContext,
                                               GrReferenceExpression place) {
    if (!(type instanceof PsiClassType)) return true;

    final PsiClass psiClass = ((PsiClassType)type).resolve();
    if (psiClass == null || !CommonClassNames.JAVA_LANG_CLASS.equals(psiClass.getQualifiedName())) return true;

    final PsiType[] params = ((PsiClassType)type).getParameters();
    if (params.length != 1) return true;

    if (!processQualifierType(processor, params[0], ResolveState.initial().put(ResolverProcessor.RESOLVE_CONTEXT, resolveContext), place)) return false;
    return true;
  }

  @Nullable
  private static PsiType getComponentTypeForSpreadDot(@Nullable PsiType qualifierType, @NotNull GroovyPsiElement place) {
    if (qualifierType instanceof PsiArrayType) {
      return ((PsiArrayType)qualifierType).getComponentType();
    }
    //process for collections
    if (!(qualifierType instanceof PsiClassType)) return null;

    PsiClassType.ClassResolveResult result = ((PsiClassType)qualifierType).resolveGenerics();
    PsiClass clazz = result.getElement();
    if (clazz == null) return null;

    PsiClass collection = GroovyPsiManager.getInstance(place.getProject()).findClassWithCache(CommonClassNames.JAVA_UTIL_COLLECTION,
                                                                                              place.getResolveScope());
    if (collection == null || collection.getTypeParameters().length != 1) return null;

    PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(collection, clazz, result.getSubstitutor());
    if (substitutor == null) return null;

    return substitutor.substitute(collection.getTypeParameters()[0]);
  }

  public static boolean processQualifier(PsiScopeProcessor processor, GrExpression qualifier, GrReferenceExpression place) {
    PsiType qualifierType = qualifier.getType();
    ResolveState state = ResolveState.initial().put(ResolverProcessor.RESOLVE_CONTEXT, qualifier);
    if (qualifierType == null || qualifierType == PsiType.VOID) {
      if (qualifier instanceof GrReferenceExpression) {
        PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
        if (resolved != null && !resolved.processDeclarations(processor, state, null, place)) return false;
        if (!(resolved instanceof PsiPackage)) {
          qualifierType = TypesUtil.getJavaLangObject(place);
          if (!processQualifierType(processor, qualifierType, state, place)) return false;
        }
      }
    }
    else {
      if (qualifierType instanceof PsiIntersectionType) {
        for (PsiType conjunct : ((PsiIntersectionType)qualifierType).getConjuncts()) {
          if (!processQualifierType(processor, conjunct, state, place)) return false;
        }
      }
      else {
        if (!processQualifierType(processor, qualifierType, state, place)) return false;
        if (qualifier instanceof GrReferenceExpression) {
          PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
          if (resolved instanceof PsiClass) { //omitted .class
            PsiClass javaLangClass = PsiUtil.getJavaLangClass(resolved, place.getResolveScope());
            if (javaLangClass != null) {
              PsiTypeParameter[] typeParameters = javaLangClass.getTypeParameters();
              PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
              if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;
              if (typeParameters.length == 1) {
                substitutor = substitutor.put(typeParameters[0], qualifierType);
                state = state.put(PsiSubstitutor.KEY, substitutor);
              }
              if (!javaLangClass.processDeclarations(processor, state, null, place)) return false;
              PsiType javaLangClassType = TypesUtil.createJavaLangClassType(qualifierType, place.getProject(), place.getResolveScope());
              if (!ResolveUtil.processNonCodeMembers(javaLangClassType, processor, place, state)) return false;
            }
          }
        }
      }
    }
    return true;
  }

  private static boolean processQualifierType(final PsiScopeProcessor processor,
                                              final PsiType originalQualifierType,
                                              final ResolveState state,
                                              final GrReferenceExpression place) {
    PsiType qualifierType = originalQualifierType instanceof PsiDisjunctionType
                    ? ((PsiDisjunctionType)originalQualifierType).getLeastUpperBound()
                    : originalQualifierType;

    if (qualifierType instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)qualifierType).getConjuncts()) {
        if (!processQualifierType(processor, conjunct, state, place)) return false;
      }
      return true;
    }

    if (qualifierType instanceof PsiClassType) {
      PsiClassType.ClassResolveResult qualifierResult = ((PsiClassType)qualifierType).resolveGenerics();
      PsiClass qualifierClass = qualifierResult.getElement();
      if (qualifierClass != null) {
        if (!qualifierClass.processDeclarations(processor, state.put(PsiSubstitutor.KEY, qualifierResult.getSubstitutor()), null, place)) return false;
      }
    }
    else if (qualifierType instanceof PsiArrayType) {
      final GrTypeDefinition arrayClass = GroovyPsiManager.getInstance(place.getProject()).getArrayClass();
      if (!arrayClass.processDeclarations(processor, state, null, place)) return false;
    }

    if (!(place.getParent() instanceof GrMethodCall)) {
      final PsiType componentType = getComponentTypeForSpreadDot(qualifierType, place);
      if (componentType != null) {
        final SpreadState spreadState = state.get(SpreadState.SPREAD_STATE);
        processQualifierType(processor, componentType, state.put(SpreadState.SPREAD_STATE, SpreadState.create(qualifierType, spreadState)), place);
      }
    }

    if (!ResolveUtil.processCategoryMembers(place, processor, state)) return false;
    if (!ResolveUtil.processNonCodeMembers(qualifierType, processor, place, state)) return false;
    return true;
  }

  @Nullable
  public static PsiType getQualifierType(GrReferenceExpression ref) {
    final GrExpression rtQualifier = PsiImplUtil.getRuntimeQualifier(ref);
    if (rtQualifier != null) {
      return rtQualifier.getType();
    }

    PsiClass containingClass = null;
    final GrMember member = PsiTreeUtil.getParentOfType(ref, GrMember.class);
    if (member == null) {
      final PsiFile file = ref.getContainingFile();
      if (file instanceof GroovyFileBase && ((GroovyFileBase)file).isScript()) {
        containingClass = ((GroovyFileBase)file).getScriptClass();
      }
      else {
        return null;
      }
    }
    else if (member instanceof GrMethod) {
      if (!member.hasModifierProperty(PsiModifier.STATIC)) {
        containingClass = member.getContainingClass();
      }
    }

    if (containingClass != null) {
      return JavaPsiFacade.getElementFactory(ref.getProject()).createType(containingClass);
    }
    return null;
  }
}
