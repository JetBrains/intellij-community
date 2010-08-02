/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.CompletionProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.*;

/**
 * @author ven
 */
public class CompleteReferenceExpression {
  private CompleteReferenceExpression() {
  }

  public static void processVariants(Consumer<Object> consumer, GrReferenceExpressionImpl refExpr) {
    final GrExpression qualifier = refExpr.getQualifierExpression();
    Object[] propertyVariants = getVariantsImpl(refExpr, CompletionProcessor.createPropertyCompletionProcessor(refExpr));
    PsiType type = null;
    if (qualifier != null) {
      type = qualifier.getType();
    }

    if (type instanceof PsiClassType) {
      PsiClass clazz = ((PsiClassType)type).resolve();
      if (clazz != null) {
        Set<String> accessedPropertyNames = processPropertyVariants(consumer, refExpr, clazz);
        for (Object variant : propertyVariants) {
          if (variant instanceof PsiField && accessedPropertyNames.contains(((PsiField)variant).getName())) {
            continue;
          }
          else if (variant instanceof LookupElement) {
            final Object o = ((LookupElement)variant).getObject();
            if (o instanceof PsiField && accessedPropertyNames.contains(((PsiField)o).getName())) {
              continue;
            }
          }
          consumer.consume(variant);
        }
      }
    }
    else {
      for (Object variant : propertyVariants) {
        consumer.consume(variant);
      }
    }

    if (refExpr.getKind() == GrReferenceExpressionImpl.Kind.TYPE_OR_PROPERTY) {
      ResolverProcessor classVariantsCollector = CompletionProcessor.createClassCompletionProcessor(refExpr);
      final Object[] classVariants = getVariantsImpl(refExpr, classVariantsCollector);
      for (Object variant : classVariants) {
        consumer.consume(variant);
      }
    }
  }



    private static void processIfJavaLangClass(GrReferenceExpression refExpr, ResolverProcessor consumer, PsiType type) {
      if (type instanceof PsiClassType) {
        final PsiClass psiClass = ((PsiClassType)type).resolve();
        if (psiClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(psiClass.getQualifiedName())) {
          final PsiType[] params = ((PsiClassType)type).getParameters();
          if (params.length == 1) {
            getVariantsFromQualifierType(refExpr, consumer, params[0], refExpr.getProject());
          }
        }
      }
    }

  private static Set<String> processPropertyVariants(Consumer<Object> consumer, GrReferenceExpression refExpr, PsiClass clazz) {
    Set<String> accessedPropertyNames = new THashSet<String>();
    final PsiClass eventListener =
      JavaPsiFacade.getInstance(refExpr.getProject()).findClass("java.util.EventListener", refExpr.getResolveScope());
    for (PsiMethod method : clazz.getAllMethods()) {
      if (PsiUtil.isStaticsOK(method, refExpr)) {
        if (isSimplePropertyAccessor(method)) {
          String prop = getPropertyName(method);
          accessedPropertyNames.add(prop);
          assert prop != null;
          consumer.consume(LookupElementBuilder.create(prop).setIcon(GroovyIcons.PROPERTY));
        }
        else if (eventListener != null) {
          consumeListenerProperties(consumer, method, eventListener);
        }
      }
    }
    return accessedPropertyNames;
  }

  private static void consumeListenerProperties(Consumer<Object> consumer, PsiMethod method, PsiClass eventListenerClass) {
    if (method.getName().startsWith("add") && method.getParameterList().getParametersCount() == 1) {
      final PsiParameter parameter = method.getParameterList().getParameters()[0];
      final PsiType type = parameter.getType();
      if (type instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)type;
        final PsiClass listenerClass = classType.resolve();
        if (listenerClass != null) {
          final PsiMethod[] listenerMethods = listenerClass.getMethods();
          if (InheritanceUtil.isInheritorOrSelf(listenerClass, eventListenerClass, true)) {
            for (PsiMethod listenerMethod : listenerMethods) {
              consumer.consume(LookupElementBuilder.create(listenerMethod.getName()).setIcon(GroovyIcons.PROPERTY));
            }
          }
        }
      }
    }
  }


  private static Object[] getVariantsImpl(GrReferenceExpression refExpr, ResolverProcessor processor) {
    GrExpression qualifier = refExpr.getQualifierExpression();
    String[] sameQualifier = getVariantsWithSameQualifier(qualifier, refExpr);
    if (qualifier == null) {
      ResolveUtil.treeWalkUp(refExpr, processor, true);
      qualifier = PsiImplUtil.getRuntimeQualifier(refExpr);
      if (qualifier != null) {
        getVariantsFromQualifier(refExpr, processor, qualifier);
      }
    }
    else {
      if (refExpr.getDotTokenType() != GroovyTokenTypes.mSPREAD_DOT) {
        getVariantsFromQualifier(refExpr, processor, qualifier);

        if (qualifier instanceof GrReferenceExpression && "class".equals(((GrReferenceExpression)qualifier).getReferenceName())) {
          processIfJavaLangClass(refExpr, processor, qualifier.getType());
        }
        else if (qualifier instanceof GrThisReferenceExpression) {
          processIfJavaLangClass(refExpr, processor, qualifier.getType());
        }
      }
      else {
        getVariantsFromQualifierForSpreadOperator(refExpr, processor, qualifier);
      }
    }

    GroovyResolveResult[] candidates = processor.getCandidates();
    if (candidates.length == 0 && sameQualifier.length == 0) return PsiNamedElement.EMPTY_ARRAY;
    candidates = filterStaticsOK(candidates);
    if (qualifier == null) {
      List<GroovyResolveResult> nonPackages = ContainerUtil.findAll(candidates, new Condition<GroovyResolveResult>() {
        public boolean value(final GroovyResolveResult result) {
          return !(result.getElement() instanceof PsiPackage);
        }
      });
      candidates = nonPackages.toArray(new GroovyResolveResult[nonPackages.size()]);
    }
    LookupElement[] propertyLookupElements = addPretendedProperties(candidates);
    Object[] variants = GroovyCompletionUtil.getCompletionVariants(candidates);
    variants = ArrayUtil.mergeArrays(variants, propertyLookupElements, Object.class);
    return ArrayUtil.mergeArrays(variants, sameQualifier, Object.class);
  }

  private static GroovyResolveResult[] filterStaticsOK(GroovyResolveResult[] candidates) {
    List<GroovyResolveResult> result = new ArrayList<GroovyResolveResult>(candidates.length);
    for (GroovyResolveResult resolveResult : candidates) {
      if (resolveResult.isStaticsOK() || resolveResult.getCurrentFileResolveContext() instanceof GrMethodCallExpression) {
        result.add(resolveResult);
      }
    }
    return result.toArray(new GroovyResolveResult[result.size()]);
  }

  private static void getVariantsFromQualifierForSpreadOperator(GrReferenceExpression refExpr,
                                                                ResolverProcessor processor,
                                                                GrExpression qualifier) {
    PsiType qualifierType = qualifier.getType();
    if (qualifierType instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = ((PsiClassType)qualifierType).resolveGenerics();
      PsiClass clazz = result.getElement();
      if (clazz != null) {
        PsiClass listClass = JavaPsiFacade.getInstance(refExpr.getProject()).findClass("java.util.List", refExpr.getResolveScope());
        if (listClass != null && listClass.getTypeParameters().length == 1) {
          PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(listClass, clazz, result.getSubstitutor());
          if (substitutor != null) {
            PsiType componentType = substitutor.substitute(listClass.getTypeParameters()[0]);
            if (componentType != null) {
              getVariantsFromQualifierType(refExpr, processor, componentType, refExpr.getProject());
            }
          }
        }
      }
    }
    else if (qualifierType instanceof PsiArrayType) {
      getVariantsFromQualifierType(refExpr, processor, ((PsiArrayType)qualifierType).getComponentType(), refExpr.getProject());
    }
  }

  private static LookupElement[] addPretendedProperties(GroovyResolveResult[] elements) {
    List<LookupElement> result = new ArrayList<LookupElement>();

    for (GroovyResolveResult resolveResult : elements) {
      PsiElement element = resolveResult.getElement();
      if (element instanceof PsiMethod && !(element instanceof GrAccessorMethod)) {
        PsiMethod method = (PsiMethod)element;
        String propName = null;
        if (isSimplePropertyGetter(method, null, resolveResult.getCurrentFileResolveContext() instanceof GrMethodCallExpression)) {
          propName = getPropertyNameByGetter(method);
        }
        else if (isSimplePropertySetter(method, null, resolveResult.getCurrentFileResolveContext() instanceof GrMethodCallExpression)) {
          propName = getPropertyName(method);
        }
        if (propName != null) {
          if (!PsiUtil.isValidReferenceName(propName)) {
            propName = "'" + propName + "'";
          }
          result.add(LookupElementBuilder.create(propName).setIcon(GroovyIcons.PROPERTY));
        }
      }
    }

    return result.toArray(new LookupElement[result.size()]);
  }

  private static void getVariantsFromQualifier(GrReferenceExpression refExpr, ResolverProcessor processor, GrExpression qualifier) {
    Project project = qualifier.getProject();
    PsiType qualifierType = qualifier.getType();
    if (qualifierType == null) {
      if (qualifier instanceof GrReferenceExpression) {
        PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
        if (resolved instanceof PsiPackage) {
          resolved.processDeclarations(processor, ResolveState.initial(), null, refExpr);
          return;
        }
      }
      final PsiClassType type = JavaPsiFacade.getInstance(refExpr.getProject()).getElementFactory()
        .createTypeByFQClassName(GrTypeDefinition.DEFAULT_BASE_CLASS_NAME, refExpr.getResolveScope());
      getVariantsFromQualifierType(refExpr, processor, type, project);
    }
    else {
      if (qualifierType instanceof PsiIntersectionType) {
        for (PsiType conjunct : ((PsiIntersectionType)qualifierType).getConjuncts()) {
          getVariantsFromQualifierType(refExpr, processor, conjunct, project);
        }
      }
      else {
        getVariantsFromQualifierType(refExpr, processor, qualifierType, project);
        if (qualifier instanceof GrReferenceExpression) {
          PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
          if (resolved instanceof PsiClass) { ////omitted .class
            GlobalSearchScope scope = refExpr.getResolveScope();
            PsiClass javaLangClass = PsiUtil.getJavaLangClass(resolved, scope);
            if (javaLangClass != null) {
              PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
              PsiTypeParameter[] typeParameters = javaLangClass.getTypeParameters();
              if (typeParameters.length == 1) {
                substitutor = substitutor.put(typeParameters[0], qualifierType);
              }
              javaLangClass.processDeclarations(processor, ResolveState.initial(), null, refExpr);
              PsiType javaLangClassType =
                JavaPsiFacade.getInstance(refExpr.getProject()).getElementFactory().createType(javaLangClass, substitutor);
              ResolveUtil.processNonCodeMethods(javaLangClassType, processor, refExpr);
            }
          }
        }
      }
    }
  }

  private static String[] getVariantsWithSameQualifier(GrExpression qualifier, GrReferenceExpression refExpr) {
    if (qualifier != null && qualifier.getType() != null) return ArrayUtil.EMPTY_STRING_ARRAY;

    final PsiElement scope = PsiTreeUtil.getParentOfType(refExpr, GrMember.class, GroovyFileBase.class);
    Set<String> result = new LinkedHashSet<String>();
    addVariantsWithSameQualifier(scope, refExpr, qualifier, result);
    return ArrayUtil.toStringArray(result);
  }

  private static void addVariantsWithSameQualifier(PsiElement element,
                                                   GrReferenceExpression patternExpression,
                                                   GrExpression patternQualifier,
                                                   Set<String> result) {
    if (element instanceof GrReferenceExpression && element != patternExpression && !PsiUtil.isLValue((GroovyPsiElement)element)) {
      final GrReferenceExpression refExpr = (GrReferenceExpression)element;
      final String refName = refExpr.getReferenceName();
      if (refName != null && !result.contains(refName)) {
        final GrExpression hisQualifier = refExpr.getQualifierExpression();
        if (hisQualifier != null && patternQualifier != null) {
          if (PsiEquivalenceUtil.areElementsEquivalent(hisQualifier, patternQualifier)) {
            if (refExpr.resolve() == null) {
              result.add(refName);
            }
          }
        }
        else if (hisQualifier == null && patternQualifier == null) {
          if (refExpr.resolve() == null) {
            result.add(refName);
          }
        }
      }
    }

    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      addVariantsWithSameQualifier(child, patternExpression, patternQualifier, result);
    }
  }

  private static void getVariantsFromQualifierType(GrReferenceExpression refExpr,
                                                   ResolverProcessor processor,
                                                   PsiType qualifierType,
                                                   Project project) {
    if (qualifierType instanceof PsiClassType) {
      PsiClass qualifierClass = ((PsiClassType)qualifierType).resolve();
      if (qualifierClass != null) {
        qualifierClass.processDeclarations(processor, ResolveState.initial(), null, refExpr);
      }
      if (!ResolveUtil.processCategoryMembers(refExpr, processor)) return;
    }
    else if (qualifierType instanceof PsiArrayType) {
      final GrTypeDefinition arrayClass = GroovyPsiManager.getInstance(project).getArrayClass();
      if (!arrayClass.processDeclarations(processor, ResolveState.initial(), null, refExpr)) return;
    }
    else if (qualifierType instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)qualifierType).getConjuncts()) {
        getVariantsFromQualifierType(refExpr, processor, conjunct, project);
      }
      return;
    }

    ResolveUtil.processNonCodeMethods(qualifierType, processor, refExpr);
  }
}
