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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParameterEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.CompletionProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.List;

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
        final PsiType componentType = ClosureParameterEnhancer.findTypeForIteration(qtype, place);
        if (componentType != null) {
          final ResolveState state = ResolveState.initial()
            .put(ResolverProcessor.RESOLVE_CONTEXT, qualifier)
            .put(SpreadState.SPREAD_STATE, SpreadState.create(qtype, null));
          if (!processQualifierType(processor, componentType, state, place)) return false;
        }
      }
      else {
        if (GrUnresolvedAccessInspection.isClassReference(place)) return true;
        if (!processQualifier(processor, qualifier, place)) return false;
      }

      if (qualifier instanceof GrReferenceExpression &&
          ("class".equals(((GrReferenceExpression)qualifier).getReferenceName()) || PsiUtil.isThisReference(qualifier))) {
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
                                                @Nullable PsiType type,
                                                GroovyPsiElement resolveContext,
                                                GrReferenceExpression place) {
    if (!(type instanceof PsiClassType)) return true;

    final PsiClass psiClass = ((PsiClassType)type).resolve();
    if (psiClass == null || !CommonClassNames.JAVA_LANG_CLASS.equals(psiClass.getQualifiedName())) return true;

    final PsiType[] params = ((PsiClassType)type).getParameters();
    if (params.length != 1) return true;

    if (!processQualifierType(processor, params[0], ResolveState.initial().put(ResolverProcessor.RESOLVE_CONTEXT, resolveContext), place)) {
      return false;
    }
    return true;
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
    else if (qualifierType instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)qualifierType).getConjuncts()) {
        if (!processQualifierType(processor, conjunct, state, place)) return false;
      }
    }
    else {
      if (!processQualifierType(processor, qualifierType, state, place)) return false;
      if (qualifier instanceof GrReferenceExpression && !PsiUtil.isSuperReference(qualifier) && !PsiUtil.isInstanceThisRef(qualifier)) {
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
            if (javaLangClassType != null) {
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

    /*if (qualifierType instanceof GrAnonymousClassType) {
      final GrAnonymousClassDefinition anonymous = ((GrAnonymousClassType)qualifierType).getAnonymous();
      if (!anonymous.processDeclarations(processor, state, null, place)) {
        return false;
      }
    }
    else */if (qualifierType instanceof PsiClassType) {
      PsiClassType.ClassResolveResult qualifierResult = ((PsiClassType)qualifierType).resolveGenerics();
      PsiClass qualifierClass = qualifierResult.getElement();
      if (qualifierClass != null) {
        if (!qualifierClass.processDeclarations(processor, state.put(PsiSubstitutor.KEY, qualifierResult.getSubstitutor()), null, place)) {
          return false;
        }
      }
    }
    else if (qualifierType instanceof PsiArrayType) {
      final GroovyPsiManager gmanager = GroovyPsiManager.getInstance(place.getProject());
      final GrTypeDefinition arrayClass = gmanager.getArrayClass(((PsiArrayType)qualifierType).getComponentType());
      if (arrayClass != null && !arrayClass.processDeclarations(processor, state, null, place)) return false;
    }

    if (!(place.getParent() instanceof GrMethodCall) && InheritanceUtil.isInheritor(qualifierType, CommonClassNames.JAVA_UTIL_COLLECTION)) {
      final PsiType componentType = ClosureParameterEnhancer.findTypeForIteration(qualifierType, place);
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
      final PsiClassType categoryType = GdkMethodUtil.getCategoryType(containingClass);
      if (categoryType != null) {
        return categoryType;
      }
      return JavaPsiFacade.getElementFactory(ref.getProject()).createType(containingClass);
    }
    return null;
  }

  public static boolean resolveThisExpression(GrReferenceExpression ref, List<GroovyResolveResult> results) {
    GrExpression qualifier = ref.getQualifier();

    if (qualifier == null) {
      final PsiElement parent = ref.getParent();
      if (parent instanceof GrConstructorInvocation) {
        GroovyResolveResult[] res = ((GrConstructorInvocation)parent).multiResolve(false);
        ContainerUtil.addAll(results, res);
        return true;
      }

      PsiClass aClass = PsiUtil.getContextClass(ref);
      if (aClass == null) return false;

      results.add(new GroovyResolveResultImpl(aClass, null, null, PsiSubstitutor.EMPTY, true, true));
      return true;
    }
    else {
      if (!(qualifier instanceof GrReferenceExpression)) return false;

      GroovyResolveResult result = ((GrReferenceExpression)qualifier).advancedResolve();
      PsiElement resolved = result.getElement();
      if (!(resolved instanceof PsiClass)) return false;
      if (!PsiUtil.hasEnclosingInstanceInScope((PsiClass)resolved, ref, false)) return false;

      results.add(result);
      return true;
    }
  }

  public static boolean resolveSuperExpression(GrReferenceExpression ref, List<GroovyResolveResult> results) {
    GrExpression qualifier = ref.getQualifier();

    PsiClass aClass;
    if (qualifier == null) {
      final PsiElement parent = ref.getParent();
      if (parent instanceof GrConstructorInvocation) {
        GroovyResolveResult[] res = ((GrConstructorInvocation)parent).multiResolve(false);
        ContainerUtil.addAll(results, res);
        return true;
      }

      aClass = PsiUtil.getContextClass(ref);
      if (aClass == null) return false;
    }
    else {
      if (!(qualifier instanceof GrReferenceExpression)) return false;

      GroovyResolveResult result = ((GrReferenceExpression)qualifier).advancedResolve();
      PsiElement resolved = result.getElement();
      if (!(resolved instanceof PsiClass)) return false;
      if (!PsiUtil.hasEnclosingInstanceInScope((PsiClass)resolved, ref, false)) return false;

      aClass = (PsiClass)resolved;
    }
    PsiClass superClass = aClass.getSuperClass();
    if (superClass == null) return true; //no super class, but the reference is definitely super-reference

    PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
    results.add(new GroovyResolveResultImpl(superClass, null, null, superClassSubstitutor, true, true));
    return true;
  }
}
