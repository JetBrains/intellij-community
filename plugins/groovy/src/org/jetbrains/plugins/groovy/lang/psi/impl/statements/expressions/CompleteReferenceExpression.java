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
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ClosureMissingMethodContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.*;

/**
 * @author ven
 */
public class CompleteReferenceExpression {
  private CompleteReferenceExpression() {
  }

  public static void processVariants(PrefixMatcher matcher, Consumer<Object> consumer, GrReferenceExpressionImpl refExpr) {
    final CompleteReferenceProcessor processor = new CompleteReferenceProcessor(refExpr, consumer);
    getVariantsImpl(matcher, refExpr, processor);
    final GroovyResolveResult[] candidates = processor.getCandidates();
    for (Object o : GroovyCompletionUtil.getCompletionVariants(candidates)) {
      consumer.consume(o);
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

  private static void getVariantsImpl(PrefixMatcher matcher, GrReferenceExpression refExpr, CompleteReferenceProcessor processor) {
    GrExpression qualifier = refExpr.getQualifierExpression();
    getVariantsWithSameQualifier(matcher, qualifier, refExpr, processor);
    if (qualifier == null) {
      ResolveUtil.treeWalkUp(refExpr, processor, true);

      for (PsiElement e = refExpr.getParent(); e != null; e = e.getParent()) {
        if (e instanceof GrClosableBlock) {
          ResolveState state = ResolveState.initial().put(ResolverProcessor.RESOLVE_CONTEXT, (GrClosableBlock)e);
          for (ClosureMissingMethodContributor contributor : ClosureMissingMethodContributor.EP_NAME.getExtensions()) {
            contributor.processMembers((GrClosableBlock)e, processor, refExpr, state);
          }
        }
      }

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
    ResolveUtil.processCategoryMembers(refExpr, processor);
  }

  private static void getVariantsFromQualifierForSpreadOperator(GrReferenceExpression refExpr,
                                                                ResolverProcessor processor,
                                                                GrExpression qualifier) {
    PsiType qualifierType = qualifier.getType();
    if (qualifierType instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = ((PsiClassType)qualifierType).resolveGenerics();
      PsiClass clazz = result.getElement();
      if (clazz != null) {
        PsiClass listClass =
          JavaPsiFacade.getInstance(refExpr.getProject()).findClass(CommonClassNames.JAVA_UTIL_COLLECTION, refExpr.getResolveScope());
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

  @NotNull
  public static LookupElementBuilder createPropertyLookupElement(@NotNull String name, @NotNull PsiType type) {
    return LookupElementBuilder.create(name).setIcon(GroovyIcons.PROPERTY).setTypeText(type.getPresentableText());
  }

  @Nullable
  public static LookupElementBuilder createPropertyLookupElement(@NotNull PsiMethod accessor, @Nullable GroovyResolveResult resolveResult) {
    String propName;
    PsiType propType;
    final boolean inUseScope = ResolveUtil.isInUseScope(resolveResult);
    if (isSimplePropertyGetter(accessor, null, inUseScope)) {
      propName = getPropertyNameByGetter(accessor);
      propType = PsiUtil.getSmartReturnType(accessor);
    }
    else if (isSimplePropertySetter(accessor, null, inUseScope)) {
      propName = getPropertyNameBySetter(accessor);
      propType = accessor.getParameterList().getParameters()[inUseScope ? 1 : 0].getType();
    }
    else {
      return null;
    }
    assert propName != null;
    if (!PsiUtil.isValidReferenceName(propName)) {
      propName = "'" + propName + "'";
    }

    final PsiType substituted = resolveResult != null ? resolveResult.getSubstitutor().substitute(propType) : propType;

    LookupElementBuilder builder =
      LookupElementBuilder.create(generatePropertyResolveResult(propName, accessor, propType, resolveResult), propName)
        .setIcon(GroovyIcons.PROPERTY);
    if (substituted != null) {
      builder = builder.setTypeText(substituted.getPresentableText());
    }
    return builder;
  }

  private static GroovyResolveResult generatePropertyResolveResult(@NotNull String name,
                                                                   @NotNull PsiMethod method,
                                                                   @Nullable PsiType type,
                                                                   @Nullable GroovyResolveResult resolveResult) {
    if (type == null) {
      type = TypesUtil.getJavaLangObject(method);
    }

    final GrPropertyForCompletion field = new GrPropertyForCompletion(method, name, type);
    if (resolveResult != null) {
      return new GroovyResolveResultImpl(field, resolveResult.getCurrentFileResolveContext(), resolveResult.getSubstitutor(),
                                         resolveResult.isAccessible(), resolveResult.isStaticsOK());
    }
    else {
      return new GroovyResolveResultImpl(field, true);
    }
  }

  private static void getVariantsFromQualifier(GrReferenceExpression refExpr, ResolverProcessor processor, GrExpression qualifier) {
    Project project = qualifier.getProject();
    PsiType qualifierType = qualifier.getType();
    final ResolveState state = ResolveState.initial();
    if (qualifierType == null) {
      if (qualifier instanceof GrReferenceExpression) {
        PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
        if (resolved instanceof PsiPackage) {
          resolved.processDeclarations(processor, state, null, refExpr);
          return;
        }
      }
      getVariantsFromQualifierType(refExpr, processor, GrClassImplUtil.getGroovyObjectType(refExpr), project);
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
              javaLangClass.processDeclarations(processor, state, null, refExpr);
              PsiType javaLangClassType =
                JavaPsiFacade.getInstance(refExpr.getProject()).getElementFactory().createType(javaLangClass, substitutor);
              ResolveUtil.processNonCodeMethods(javaLangClassType, processor, refExpr, state);
            }
          }
        }
      }
    }
  }

  private static String[] getVariantsWithSameQualifier(PrefixMatcher matcher,GrExpression qualifier, GrReferenceExpression refExpr, CompleteReferenceProcessor processor) {
    if (qualifier != null && qualifier.getType() != null) return ArrayUtil.EMPTY_STRING_ARRAY;

    final PsiElement scope = PsiTreeUtil.getParentOfType(refExpr, GrMember.class, GroovyFileBase.class);
    Set<String> result = new LinkedHashSet<String>();
    addVariantsWithSameQualifier(matcher, scope, refExpr, qualifier, result, processor);
    return ArrayUtil.toStringArray(result);
  }

  private static void addVariantsWithSameQualifier(PrefixMatcher matcher, PsiElement element,
                                                   GrReferenceExpression patternExpression,
                                                   GrExpression patternQualifier,
                                                   Set<String> result,
                                                   CompleteReferenceProcessor processor) {
    if (element instanceof GrReferenceExpression && element != patternExpression && !PsiUtil.isLValue((GroovyPsiElement)element)) {
      final GrReferenceExpression refExpr = (GrReferenceExpression)element;
      final String refName = refExpr.getReferenceName();
      if (refName != null && !result.contains(refName) && matcher.prefixMatches(refName)) {
        final GrExpression hisQualifier = refExpr.getQualifierExpression();
        if (hisQualifier != null && patternQualifier != null) {
          if (PsiEquivalenceUtil.areElementsEquivalent(hisQualifier, patternQualifier)) {
            if (refExpr.resolve() == null) {
              result.add(refName);
              processor.consume(refName);
            }
          }
        }
        else if (hisQualifier == null && patternQualifier == null) {
          if (refExpr.resolve() == null) {
            result.add(refName);
            processor.consume(refName);
          }
        }
      }
    }

    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      addVariantsWithSameQualifier(matcher, child, patternExpression, patternQualifier, result, processor);
    }
  }

  private static void getVariantsFromQualifierType(GrReferenceExpression refExpr,
                                                   ResolverProcessor processor,
                                                   PsiType qualifierType,
                                                   Project project) {
    final ResolveState state = ResolveState.initial();
    if (qualifierType instanceof PsiClassType) {
      PsiClass qualifierClass = ((PsiClassType)qualifierType).resolve();
      if (qualifierClass != null) {
        qualifierClass.processDeclarations(processor, state, null, refExpr);
      }
    }
    else if (qualifierType instanceof PsiArrayType) {
      final GrTypeDefinition arrayClass = GroovyPsiManager.getInstance(project).getArrayClass();
      if (!arrayClass.processDeclarations(processor, state, null, refExpr)) return;
    }
    else if (qualifierType instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)qualifierType).getConjuncts()) {
        getVariantsFromQualifierType(refExpr, processor, conjunct, project);
      }
      return;
    }
    ResolveUtil.processNonCodeMethods(qualifierType, processor, refExpr, state);
  }

  private static Set<String> addAllRestrictedProperties(GrReferenceExpression place) {
    Set<String> propertyNames = new HashSet<String>();
    final PsiElement qualifier = place.getQualifier();
    if (qualifier == null) {
      for (GrTypeDefinition containingClass = PsiTreeUtil.getParentOfType(place, GrTypeDefinition.class);
           containingClass != null;
           containingClass = PsiTreeUtil.getParentOfType(containingClass, GrTypeDefinition.class)) {
        for (PsiField field : containingClass.getAllFields()) {
          propertyNames.add(field.getName());
        }
      }
    }
    return propertyNames;
  }

  private static class CompleteReferenceProcessor extends ResolverProcessor implements Consumer<Object> {
    private final Consumer<Object> myConsumer;
    private Collection<String> myPreferredFieldNames;
    private final boolean mySkipPackages;
    private final PsiClass myEventListener;
    private final Set<String> myPropertyNames = new HashSet<String>();
    private final Set<String> myLocalVars = new HashSet<String>();

    protected CompleteReferenceProcessor(GrReferenceExpression place, Consumer<Object> consumer) {
      super(null, EnumSet.allOf(ResolveKind.class), place, PsiType.EMPTY_ARRAY);
      myConsumer = consumer;
      myPreferredFieldNames = addAllRestrictedProperties(place);
      mySkipPackages = PsiImplUtil.getRuntimeQualifier(place) == null;
      myEventListener = JavaPsiFacade.getInstance(place.getProject()).findClass("java.util.EventListener", place.getResolveScope());
      myPropertyNames.addAll(addAllRestrictedProperties(place));
    }

    @Override
    public boolean execute(PsiElement element, ResolveState state) {
      if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) return true;

      PsiNamedElement namedElement = (PsiNamedElement)element;

      boolean isAccessible = isAccessible(namedElement);
      final GroovyPsiElement resolveContext = state.get(RESOLVE_CONTEXT);
      boolean isStaticsOK = isStaticsOK(namedElement, resolveContext);

      PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
      if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;

      consume(new GroovyResolveResultImpl(namedElement, resolveContext, substitutor, isAccessible, isStaticsOK));

      return true;
    }

    public void consume(Object o) {
      if (o instanceof GroovyResolveResult) {
        final GroovyResolveResult result = (GroovyResolveResult)o;
        if (!result.isStaticsOK()) return;
        if (mySkipPackages && result.getElement() instanceof PsiPackage) return;

        addCandidate(result);
        //myConsumer.consume(GroovyCompletionUtil.createCompletionVariant(result));


        final PsiElement element = result.getElement();
        if (element instanceof PsiMethod) {
          processProperty((PsiMethod)element, result);
        }
        else if (element instanceof GrField) {
          if (((GrField)element).isProperty()) {
            processPropertyFromField((GrField)element, result);
          }
        }
        else if (element instanceof GrVariable && ((GrVariable)element).getName()!=null) {
          myLocalVars.add(((GrVariable)element).getName());
        }
      }
      else {
        myConsumer.consume(o);
      }
    }

    private void processPropertyFromField(GrField field, GroovyResolveResult resolveResult) {
      if (field.getGetters().length == 0 && field.getSetter() == null && myPropertyNames.add(field.getName())) {
        myConsumer
          .consume(((LookupElementBuilder)GroovyCompletionUtil.createCompletionVariant(resolveResult)).setIcon(GroovyIcons.PROPERTY));
      }
    }

    private void processProperty(PsiMethod method, GroovyResolveResult resolveResult) {
      final LookupElementBuilder lookup = createPropertyLookupElement(method, resolveResult);
      if (lookup != null) {
        if (myPropertyNames.add(lookup.getLookupString())) {
          myConsumer.consume(lookup);
        }
      }
      else if (myEventListener != null) {
        processListenerProperties(method);
      }
    }

    private void processListenerProperties(PsiMethod method) {
      if (method.getName().startsWith("add") && method.getParameterList().getParametersCount() == 1) {
        final PsiParameter parameter = method.getParameterList().getParameters()[0];
        final PsiType type = parameter.getType();
        if (type instanceof PsiClassType) {
          final PsiClassType classType = (PsiClassType)type;
          final PsiClass listenerClass = classType.resolve();
          if (listenerClass != null) {
            final PsiMethod[] listenerMethods = listenerClass.getMethods();
            if (InheritanceUtil.isInheritorOrSelf(listenerClass, myEventListener, true)) {
              for (PsiMethod listenerMethod : listenerMethods) {
                final String name = listenerMethod.getName();
                if (myPropertyNames.add(name)) {
                  LookupElementBuilder builder = LookupElementBuilder
                    .create(generatePropertyResolveResult(name, listenerMethod, null, null), name)
                    .setIcon(GroovyIcons.PROPERTY);
                  myConsumer.consume(builder);
                }
              }
            }
          }
        }
      }
    }

    @NotNull
    @Override
    public GroovyResolveResult[] getCandidates() {
      if (!hasCandidates()) return GroovyResolveResult.EMPTY_ARRAY;
      final GroovyResolveResult[] results = ResolveUtil.filterSameSignatureCandidates(getCandidatesInternal());
      List<GroovyResolveResult> list = new ArrayList<GroovyResolveResult>(results.length);
      myPropertyNames.removeAll(myPreferredFieldNames);
      for (GroovyResolveResult result : results) {
        final PsiElement element = result.getElement();
        if (element instanceof PsiField &&
            (myPropertyNames.contains(((PsiField)element).getName()) || myLocalVars.contains(((PsiField)element).getName()))) {
          continue;
        }
        list.add(result);
      }
      return list.toArray(new GroovyResolveResult[list.size()]);
    }
  }
}
