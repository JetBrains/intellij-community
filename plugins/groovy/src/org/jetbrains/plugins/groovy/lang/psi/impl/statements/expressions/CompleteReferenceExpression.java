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

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.JavaClassNameCompletionContributor;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashSet;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParameterEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ClosureMissingMethodContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.SubstitutorComputer;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.*;

/**
 * @author ven
 */
public class CompleteReferenceExpression {
  private CompleteReferenceExpression() {
  }

  public static void processVariants(PrefixMatcher matcher,
                                     final Consumer<LookupElement> consumer,
                                     GrReferenceExpressionImpl refExpr,
                                     CompletionParameters parameters) {
    processRefInAnnotation(refExpr, matcher, consumer);

    final int[] count = new int[]{0};
    final CompleteReferenceProcessor processor = new CompleteReferenceProcessor(refExpr, new Consumer<LookupElement>() {
      @Override
      public void consume(LookupElement element) {
        count[0]++;
        consumer.consume(element);
      }
    }, matcher, parameters);

    getVariantsImpl(refExpr, processor);
    final GroovyResolveResult[] candidates = processor.getCandidates();
    List<LookupElement> results =
      GroovyCompletionUtil.getCompletionVariants(candidates,
                                                 JavaClassNameCompletionContributor.AFTER_NEW.accepts(refExpr), matcher, refExpr);

    if (count[0] == 0 && results.size() == 0) {
      results = GroovyCompletionUtil.getCompletionVariants(processor.getInapplicableResults(),
                                                           JavaClassNameCompletionContributor.AFTER_NEW.accepts(refExpr), matcher, refExpr);
    }
    for (LookupElement o : results) {
      consumer.consume(o);
    }
  }

  public static void processRefInAnnotation(GrReferenceExpression refExpr,
                                            PrefixMatcher matcher, Consumer<LookupElement> consumer) {
    if (refExpr.getParent() instanceof GrAnnotationNameValuePair &&
        ((GrAnnotationNameValuePair)refExpr.getParent()).getNameIdentifierGroovy() == null) {
      PsiElement parent = refExpr.getParent().getParent();
      if (!(parent instanceof GrAnnotation)) {
        parent = parent.getParent();
      }
      if (parent instanceof GrAnnotation) {
        for (LookupElement result : GroovyCompletionUtil.getAnnotationCompletionResults((GrAnnotation)parent, matcher)) {
          consumer.consume(result);
        }
      }
    }
  }

  private static void processIfJavaLangClass(GrReferenceExpression refExpr, ResolverProcessor consumer, PsiType type) {
    if (!(type instanceof PsiClassType)) return;

    final PsiClass psiClass = ((PsiClassType)type).resolve();
    if (psiClass == null || !CommonClassNames.JAVA_LANG_CLASS.equals(psiClass.getQualifiedName())) return;

    final PsiType[] params = ((PsiClassType)type).getParameters();
    if (params.length != 1) return;

    getVariantsFromQualifierType(refExpr, consumer, params[0], refExpr.getProject());
  }

  private static void getVariantsImpl(GrReferenceExpression refExpr, CompleteReferenceProcessor processor) {
    GrExpression qualifier = refExpr.getQualifierExpression();
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
      
      getBindings(refExpr, processor);
    }
    else {
      if (refExpr.getDotTokenType() != GroovyTokenTypes.mSPREAD_DOT) {
        getVariantsFromQualifier(refExpr, processor, qualifier);

        if (qualifier instanceof GrReferenceExpression &&
            ("class".equals(((GrReferenceExpression)qualifier).getReferenceName()) || PsiUtil.isThisReference(qualifier) && !PsiUtil.isInstanceThisRef(qualifier))) {
          processIfJavaLangClass(refExpr, processor, qualifier.getType());
        }
      }
      else {
        getVariantsFromQualifierForSpreadOperator(refExpr, processor, qualifier);
      }
    }
    ResolveUtil.processCategoryMembers(refExpr, processor, ResolveState.initial());
  }

  private static void getBindings(final GrReferenceExpression refExpr, final CompleteReferenceProcessor processor) {
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(refExpr, PsiClass.class);
    if (containingClass != null) return;

    final PsiFile file = refExpr.getContainingFile();
    if (file instanceof GroovyFile) {
      ((GroovyFile)file).accept(new GroovyRecursiveElementVisitor() {
        @Override
        public void visitAssignmentExpression(GrAssignmentExpression expression) {
          super.visitAssignmentExpression(expression);

          final GrExpression value = expression.getLValue();
          if (value instanceof GrReferenceExpression && !((GrReferenceExpression)value).isQualified()) {
            final PsiElement resolved = ((GrReferenceExpression)value).resolve();
            if (resolved instanceof GrBindingVariable) {
              processor.execute(resolved, ResolveState.initial());
            }
            else if (resolved == null) {
              processor.execute(new GrBindingVariable((GroovyFile)file, ((GrReferenceExpression)value).getReferenceName(), true),
                                ResolveState.initial());
            }
          }
        }

        @Override
        public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
          //don't go into classes
        }
      });
    }
  }

  private static void getVariantsFromQualifierForSpreadOperator(GrReferenceExpression refExpr,
                                                                ResolverProcessor processor,
                                                                GrExpression qualifier) {
    final PsiType spreadType = ClosureParameterEnhancer.findTypeForIteration(qualifier, refExpr);
    getVariantsFromQualifierType(refExpr, processor, spreadType, refExpr.getProject());
  }

  @NotNull
  public static LookupElementBuilder createPropertyLookupElement(@NotNull String name, @Nullable PsiType type) {
    LookupElementBuilder res = LookupElementBuilder.create(name).withIcon(JetgroovyIcons.Groovy.Property);
    if (type != null) {
      res = res.withTypeText(type.getPresentableText());
    }
    return res;
  }

  @Nullable
  public static LookupElementBuilder createPropertyLookupElement(@NotNull PsiMethod accessor, @Nullable GroovyResolveResult resolveResult,
                                                                 @Nullable PrefixMatcher matcher) {
    String propName;
    PsiType propType;
    final boolean getter = isSimplePropertyGetter(accessor, null);
    if (getter) {
      propName = getPropertyNameByGetter(accessor);
    }
    else if (isSimplePropertySetter(accessor, null)) {
      propName = getPropertyNameBySetter(accessor);
    }
    else {
      return null;
    }
    assert propName != null;
    if (!PsiUtil.isValidReferenceName(propName)) {
      propName = "'" + propName + "'";
    }

    if (matcher != null && !matcher.prefixMatches(propName)) {
      return null;
    }

    if (getter) {
      propType = PsiUtil.getSmartReturnType(accessor);
    } else {
      propType = accessor.getParameterList().getParameters()[0].getType();
    }

    final PsiType substituted = resolveResult != null ? resolveResult.getSubstitutor().substitute(propType) : propType;

    LookupElementBuilder builder =
      LookupElementBuilder.create(generatePropertyResolveResult(propName, accessor, propType, resolveResult), propName)
        .withIcon(JetgroovyIcons.Groovy.Property);
    if (substituted != null) {
      builder = builder.withTypeText(substituted.getPresentableText());
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
      return new GroovyResolveResultImpl(field, resolveResult.getCurrentFileResolveContext(), resolveResult.getSpreadState(),
                                         resolveResult.getSubstitutor(), resolveResult.isAccessible(), resolveResult.isStaticsOK());
    }
    else {
      return new GroovyResolveResultImpl(field, true);
    }
  }

  private static void getVariantsFromQualifier(GrReferenceExpression refExpr, ResolverProcessor processor, GrExpression qualifier) {
    Project project = qualifier.getProject();
    PsiType qualifierType = qualifier.getType();
    final ResolveState state = ResolveState.initial();
    if (qualifierType == null || qualifierType == PsiType.VOID) {
      if (qualifier instanceof GrReferenceExpression) {
        PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
        if (resolved instanceof PsiPackage || resolved instanceof PsiVariable) {
          resolved.processDeclarations(processor, state, null, refExpr);
          return;
        }
      }
      getVariantsFromQualifierType(refExpr, processor,
                                   PsiType.getJavaLangObject(refExpr.getManager(), qualifier.getResolveScope()), project);
    }
    else if (qualifierType instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)qualifierType).getConjuncts()) {
        getVariantsFromQualifierType(refExpr, processor, conjunct, project);
      }
    }
    else {
      getVariantsFromQualifierType(refExpr, processor, qualifierType, project);
      if (qualifier instanceof GrReferenceExpression && !PsiUtil.isSuperReference(qualifier) && !PsiUtil.isInstanceThisRef(qualifier)) {
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
            PsiType javaLangClassType = JavaPsiFacade.getElementFactory(refExpr.getProject()).createType(javaLangClass, substitutor);
            ResolveUtil.processAllDeclarations(javaLangClassType, processor, state, refExpr);
          }
        }
      }
    }
  }

  public static Set<String> getVariantsWithSameQualifier(PrefixMatcher matcher, @Nullable GrExpression qualifier, GrReferenceExpression refExpr) {
    if (qualifier != null && qualifier.getType() != null) return Collections.emptySet();

    final PsiElement scope = PsiTreeUtil.getParentOfType(refExpr, GrMember.class, PsiFile.class);
    Set<String> result = new LinkedHashSet<String>();
    addVariantsWithSameQualifier(matcher, scope, refExpr, qualifier, result);
    return result;
  }

  private static void addVariantsWithSameQualifier(PrefixMatcher matcher, PsiElement element,
                                                   GrReferenceExpression patternExpression,
                                                   @Nullable GrExpression patternQualifier,
                                                   Set<String> result) {
    if (element instanceof GrReferenceExpression && element != patternExpression && !PsiUtil.isLValue((GroovyPsiElement)element)) {
      final GrReferenceExpression refExpr = (GrReferenceExpression)element;
      final String refName = refExpr.getReferenceName();
      if (refName != null && !result.contains(refName) && matcher.prefixMatches(refName)) {
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
      addVariantsWithSameQualifier(matcher, child, patternExpression, patternQualifier, result);
    }
  }

  private static void getVariantsFromQualifierType(GrReferenceExpression refExpr,
                                                   ResolverProcessor processor,
                                                   PsiType qualifierType,
                                                   Project project) {
    final ResolveState state = ResolveState.initial();
    if (qualifierType instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = ((PsiClassType)qualifierType).resolveGenerics();
      PsiClass qualifierClass = result.getElement();
      if (qualifierClass != null) {
        qualifierClass.processDeclarations(processor, state.put(PsiSubstitutor.KEY, result.getSubstitutor()), null, refExpr);
      }
    }
    else if (qualifierType instanceof PsiArrayType) {
      final GrTypeDefinition arrayClass = GroovyPsiManager.getInstance(project).getArrayClass(((PsiArrayType)qualifierType).getComponentType());
      if (!arrayClass.processDeclarations(processor, state, null, refExpr)) return;
    }
    else if (qualifierType instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)qualifierType).getConjuncts()) {
        getVariantsFromQualifierType(refExpr, processor, conjunct, project);
      }
      return;
    }
    ResolveUtil.processNonCodeMembers(qualifierType, processor, refExpr, state);
  }

  private static Set<String> addAllRestrictedProperties(GrReferenceExpression place) {
    if (place.getQualifier() != null) {
      return Collections.emptySet();
    }

    Set<String> propertyNames = new HashSet<String>();
    for (GrTypeDefinition containingClass = PsiTreeUtil.getParentOfType(place, GrTypeDefinition.class);
         containingClass != null;
         containingClass = PsiTreeUtil.getParentOfType(containingClass, GrTypeDefinition.class)) {
      for (PsiField field : containingClass.getFields()) {
        propertyNames.add(field.getName());
      }
    }
    return propertyNames;
  }

  private static boolean isMap(GrReferenceExpression place) {
    final PsiType qType = GrReferenceResolveUtil.getQualifierType(place);
    return InheritanceUtil.isInheritor(qType, CommonClassNames.JAVA_UTIL_MAP);
  }

  private static class CompleteReferenceProcessor extends ResolverProcessor implements Consumer<Object> {
    private static final Logger LOG = Logger.getInstance(CompleteReferenceProcessor.class);

    private final Consumer<LookupElement> myConsumer;
    private final PrefixMatcher myMatcher;
    private final CompletionParameters myParameters;

    private final boolean mySkipPackages;
    private final PsiClass myEventListener;
    private final boolean myMethodPointerOperator;
    private final boolean myFieldPointerOperator;
    private final boolean myIsMap;

    private final SubstitutorComputer mySubstitutorComputer;

    private Collection<String> myPreferredFieldNames; //Reference is inside classes with such fields so don't suggest properties with such names.
    private final Set<String> myPropertyNames = new HashSet<String>();
    private final Set<String> myLocalVars = new HashSet<String>();
    private final Set<GrMethod> myProcessedMethodWithOptionalParams = new HashSet<GrMethod>();

    private List<GroovyResolveResult> myInapplicable;
    private GroovyResolveResult[] myInapplicableResults;

    protected CompleteReferenceProcessor(GrReferenceExpression place, Consumer<LookupElement> consumer, @NotNull PrefixMatcher matcher, CompletionParameters parameters) {
      super(null, EnumSet.allOf(ResolveKind.class), place, PsiType.EMPTY_ARRAY);
      myConsumer = consumer;
      myMatcher = matcher;
      myParameters = parameters;
      myPreferredFieldNames = addAllRestrictedProperties(place);
      mySkipPackages = shouldSkipPackages(place);
      myEventListener = JavaPsiFacade.getInstance(place.getProject()).findClass("java.util.EventListener", place.getResolveScope());
      myPropertyNames.addAll(myPreferredFieldNames);

      myFieldPointerOperator = place.hasAt();
      myMethodPointerOperator = place.getDotTokenType() == GroovyTokenTypes.mMEMBER_POINTER;
      myIsMap = isMap(place);
      final PsiType thisType = GrReferenceResolveUtil.getQualifierType(place);
      mySubstitutorComputer = new SubstitutorComputer(thisType, PsiType.EMPTY_ARRAY, PsiType.EMPTY_ARRAY, true, place, place.getParent());
    }

    private static boolean shouldSkipPackages(GrReferenceExpression place) {
      if (PsiImplUtil.getRuntimeQualifier(place) != null) {
        return false;
      }

      PsiElement parent = place.getParent();
      return parent == null || parent.getLanguage().isKindOf(GroovyFileType.GROOVY_LANGUAGE); //don't skip in Play!
    }

    @Override
    public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
      if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) return true;
      if (element instanceof PsiNamedElement) {

        PsiNamedElement namedElement = (PsiNamedElement)element;

        boolean isAccessible = isAccessible(namedElement);
        final PsiElement resolveContext = state.get(RESOLVE_CONTEXT);
        final SpreadState spreadState = state.get(SpreadState.SPREAD_STATE);
        boolean isStaticsOK = isStaticsOK(namedElement, resolveContext, myParameters.getInvocationCount() <= 1);

        PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
        if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;
        if (element instanceof PsiMethod) {
          substitutor = mySubstitutorComputer.obtainSubstitutor(substitutor, (PsiMethod)element, state);
        }

        consume(new GroovyResolveResultImpl(namedElement, resolveContext, spreadState, substitutor, isAccessible, isStaticsOK));
      }
      return true;
    }

    public void consume(Object o) {
      if (!(o instanceof GroovyResolveResult)) {
        LOG.error(o);
        return;
      }

      GroovyResolveResult result = (GroovyResolveResult)o;
      if (!result.isStaticsOK()) {
        if (myInapplicable == null) myInapplicable = ContainerUtil.newArrayList();
        myInapplicable.add(result);
        return;
      }
      if (!result.isAccessible() && myParameters.getInvocationCount() < 2) return;

      if (mySkipPackages && result.getElement() instanceof PsiPackage) return;

      PsiElement element = result.getElement();
      if (element instanceof PsiVariable && !myMatcher.prefixMatches(((PsiVariable)element).getName())) {
        return;
      }

      if (element instanceof GrReflectedMethod) {
        element = ((GrReflectedMethod)element).getBaseMethod();
        if (!myProcessedMethodWithOptionalParams.add((GrMethod)element)) return;

        result = new GroovyResolveResultImpl(element, result.getCurrentFileResolveContext(), result.getSpreadState(),
                                             result.getSubstitutor(), result.isAccessible(), result.isStaticsOK(),
                                             result.isInvokedOnProperty(), result.isValidResult());
      }

      if (myFieldPointerOperator && !(element instanceof PsiVariable)) {
        return;
      }
      if (myMethodPointerOperator && !(element instanceof PsiMethod)) {
        return;
      }
      addCandidate(result);

      if (!myFieldPointerOperator && !myMethodPointerOperator) {
        if (element instanceof PsiMethod) {
          processProperty((PsiMethod)element, result);
        }
        else if (element instanceof GrField) {
          if (((GrField)element).isProperty()) {
            processPropertyFromField((GrField)element, result);
          }
        }
      }
      if (element instanceof GrVariable && !(element instanceof GrField)) {
        myLocalVars.add(((GrVariable)element).getName());
      }
    }

    private void processPropertyFromField(GrField field, GroovyResolveResult resolveResult) {
      if (field.getGetters().length != 0 || field.getSetter() != null || !myPropertyNames.add(field.getName()) || myIsMap) return;

      for (LookupElement element : GroovyCompletionUtil.createLookupElements(resolveResult, false, myMatcher, null)) {
        myConsumer.consume(((LookupElementBuilder)element).withIcon(JetgroovyIcons.Groovy.Property));
      }

    }

    private void processProperty(PsiMethod method, GroovyResolveResult resolveResult) {
      if (myIsMap) return;
      final LookupElementBuilder lookup = createPropertyLookupElement(method, resolveResult, myMatcher);
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
      if (!method.getName().startsWith("add") || method.getParameterList().getParametersCount() != 1) return;

      final PsiParameter parameter = method.getParameterList().getParameters()[0];
      final PsiType type = parameter.getType();
      if (!(type instanceof PsiClassType)) return;

      final PsiClassType classType = (PsiClassType)type;
      final PsiClass listenerClass = classType.resolve();
      if (listenerClass == null) return;

      final PsiMethod[] listenerMethods = listenerClass.getMethods();
      if (!InheritanceUtil.isInheritorOrSelf(listenerClass, myEventListener, true)) return;

      for (PsiMethod listenerMethod : listenerMethods) {
        final String name = listenerMethod.getName();
        if (myPropertyNames.add(name)) {
          LookupElementBuilder builder = LookupElementBuilder
            .create(generatePropertyResolveResult(name, listenerMethod, null, null), name)
            .withIcon(JetgroovyIcons.Groovy.Property);
          myConsumer.consume(builder);
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

      Set<String> usedFields = ContainerUtil.newHashSet();
      for (GroovyResolveResult result : results) {
        final PsiElement element = result.getElement();
        if (element instanceof PsiField) {
          final String name = ((PsiField)element).getName();
          if (myPropertyNames.contains(name) ||
              myLocalVars.contains(name) ||
              usedFields.contains(name)) {
            continue;
          }
          else {
            usedFields.add(name);
          }
        }

        list.add(result);
      }
      return list.toArray(new GroovyResolveResult[list.size()]);
    }

    private List<GroovyResolveResult> getInapplicableResults() {
      if (myInapplicable == null) return Collections.emptyList();
      return myInapplicable;
    }
  }
}
