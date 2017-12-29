/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.JavaClassNameCompletionContributor;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashSet;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTraitType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParameterEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ClosureMissingMethodContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessorImpl;
import org.jetbrains.plugins.groovy.lang.resolve.processors.SubstitutorComputer;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.RESOLVE_CONTEXT;

/**
 * @author ven
 */
public class CompleteReferenceExpression {
  private static final Logger LOG = Logger.getInstance(CompleteReferenceExpression.class);

  private final PrefixMatcher myMatcher;
  private final Consumer<LookupElement> myConsumer;
  private final GrReferenceExpressionImpl myRefExpr;
  private final CompletionParameters myParameters;
  private final CompleteReferenceProcessor myProcessor;

  private CompleteReferenceExpression(@NotNull PrefixMatcher matcher,
                                      @NotNull Consumer<LookupElement> consumer,
                                      @NotNull GrReferenceExpressionImpl refExpr,
                                      @NotNull CompletionParameters parameters) {
    myMatcher = matcher;
    myConsumer = consumer;
    myParameters = parameters;
    myRefExpr = refExpr;

    myProcessor = new CompleteReferenceProcessor();

  }

  public static void processVariants(@NotNull PrefixMatcher matcher,
                                     @NotNull Consumer<LookupElement> consumer,
                                     @NotNull GrReferenceExpressionImpl refExpr,
                                     @NotNull CompletionParameters parameters) {
    new CompleteReferenceExpression(matcher, consumer, refExpr, parameters).processVariantsImpl();
  }

  private void processVariantsImpl() {
    processRefInAnnotationImpl();

    getVariantsImpl();
    final GroovyResolveResult[] candidates = myProcessor.getCandidates();
    List<LookupElement> results =
      GroovyCompletionUtil.getCompletionVariants(candidates,
                                                 JavaClassNameCompletionContributor.AFTER_NEW.accepts(myRefExpr), myMatcher, myRefExpr);

    if (myProcessor.isEmpty() && results.isEmpty()) {
      results = GroovyCompletionUtil.getCompletionVariants(myProcessor.getInapplicableResults(),
                                                           JavaClassNameCompletionContributor.AFTER_NEW.accepts(myRefExpr), myMatcher,
                                                           myRefExpr);
    }
    for (LookupElement o : results) {
      myConsumer.consume(o);
    }
  }

  public static void processRefInAnnotation(@NotNull GrReferenceExpression refExpr,
                                            @NotNull PrefixMatcher matcher,
                                            @NotNull Consumer<LookupElement> consumer,
                                            @NotNull CompletionParameters parameters) {
    new CompleteReferenceExpression(matcher, consumer, (GrReferenceExpressionImpl)refExpr, parameters).processRefInAnnotationImpl();
  }

  private void processRefInAnnotationImpl() {
    if (myRefExpr.getParent() instanceof GrAnnotationNameValuePair &&
        ((GrAnnotationNameValuePair)myRefExpr.getParent()).getNameIdentifierGroovy() == null) {
      PsiElement parent = myRefExpr.getParent().getParent();
      if (!(parent instanceof GrAnnotation)) {
        parent = parent.getParent();
      }
      if (parent instanceof GrAnnotation) {
        new AnnotationAttributeCompletionResultProcessor((GrAnnotation)parent).process(myConsumer, myMatcher);
      }
    }
  }

  private void processIfJavaLangClass(@Nullable PsiType type) {
    if (!(type instanceof PsiClassType)) return;

    final PsiClass psiClass = ((PsiClassType)type).resolve();
    if (psiClass == null || !CommonClassNames.JAVA_LANG_CLASS.equals(psiClass.getQualifiedName())) return;

    final PsiType[] params = ((PsiClassType)type).getParameters();
    if (params.length != 1) return;

    getVariantsFromQualifierType(params[0], myRefExpr.getProject());
  }

  private void getVariantsImpl() {
    GrExpression qualifier = myRefExpr.getQualifierExpression();
    if (qualifier == null) {
      ResolveUtil.treeWalkUp(myRefExpr, myProcessor, true);

      ClosureMissingMethodContributor.processMethodsFromClosures(myRefExpr, myProcessor);

      GrExpression runtimeQualifier = PsiImplUtil.getRuntimeQualifier(myRefExpr);
      if (runtimeQualifier != null) {
        getVariantsFromQualifier(runtimeQualifier);
      }

      getBindings();
    }
    else {
      if (myRefExpr.getDotTokenType() != GroovyTokenTypes.mSPREAD_DOT) {
        if (qualifier instanceof GrReferenceExpression && (
          "class".equals(((GrReferenceExpression)qualifier).getReferenceName()) ||
          PsiUtil.isThisReference(qualifier) && !PsiUtil.isInstanceThisRef(qualifier)
          || ((GrReferenceExpression)qualifier).resolve() instanceof PsiClass
        )) {
          processIfJavaLangClass(qualifier.getType());
        }
        getVariantsFromQualifier(qualifier);
      }
      else {
        getVariantsFromQualifierForSpreadOperator(qualifier);
      }
    }
    ResolveUtil.processCategoryMembers(myRefExpr, myProcessor, ResolveState.initial());
  }

  private void getBindings() {
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(myRefExpr, PsiClass.class);
    if (containingClass != null) return;

    final PsiFile file = FileContextUtil.getContextFile(myRefExpr);
    if (file instanceof GroovyFile) {
      ((GroovyFile)file).accept(new GroovyRecursiveElementVisitor() {
        @Override
        public void visitAssignmentExpression(@NotNull GrAssignmentExpression expression) {
          super.visitAssignmentExpression(expression);

          final GrExpression value = expression.getLValue();
          if (value instanceof GrReferenceExpression && !((GrReferenceExpression)value).isQualified()) {
            final PsiElement resolved = ((GrReferenceExpression)value).resolve();
            if (resolved instanceof GrBindingVariable) {
              myProcessor.execute(resolved, ResolveState.initial());
            }
            else if (resolved == null) {
              myProcessor.execute(new GrBindingVariable((GroovyFile)file, ((GrReferenceExpression)value).getReferenceName()),
                                ResolveState.initial());
            }
          }
        }

        @Override
        public void visitTypeDefinition(@NotNull GrTypeDefinition typeDefinition) {
          //don't go into classes
        }
      });
    }
  }

  private void getVariantsFromQualifierForSpreadOperator(@NotNull GrExpression qualifier) {
    final PsiType spreadType = ClosureParameterEnhancer.findTypeForIteration(qualifier, myRefExpr);
    if (spreadType != null) {
      getVariantsFromQualifierType(spreadType, myRefExpr.getProject());
    }
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
  public static LookupElementBuilder createPropertyLookupElement(@NotNull PsiMethod accessor,
                                                                 @Nullable GroovyResolveResult resolveResult,
                                                                 @Nullable PrefixMatcher matcher) {
    String propName;
    PsiType propType;
    final boolean getter = GroovyPropertyUtils.isSimplePropertyGetter(accessor, null);
    if (getter) {
      propName = GroovyPropertyUtils.getPropertyNameByGetter(accessor);
    }
    else if (GroovyPropertyUtils.isSimplePropertySetter(accessor, null)) {
      propName = GroovyPropertyUtils.getPropertyNameBySetter(accessor);
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

  @NotNull
  private static GroovyResolveResult generatePropertyResolveResult(@NotNull String name,
                                                                   @NotNull PsiMethod method,
                                                                   @Nullable PsiType type,
                                                                   @Nullable GroovyResolveResult resolveResult) {
    PsiType nonNullType = type != null ? type : TypesUtil.getJavaLangObject(method);

    final GrPropertyForCompletion field = new GrPropertyForCompletion(method, name, nonNullType);
    if (resolveResult != null) {
      return new GroovyResolveResultImpl(field, resolveResult.getCurrentFileResolveContext(), resolveResult.getSpreadState(),
                                         resolveResult.getSubstitutor(), resolveResult.isAccessible(), resolveResult.isStaticsOK());
    }
    else {
      return new GroovyResolveResultImpl(field, true);
    }
  }

  private void getVariantsFromQualifier(@NotNull GrExpression qualifier) {
    Project project = qualifier.getProject();
    final PsiType qualifierType = TypesUtil.boxPrimitiveType(qualifier.getType(), qualifier.getManager(), qualifier.getResolveScope());
    final ResolveState state = ResolveState.initial();
    if (qualifierType == null || PsiType.VOID.equals(qualifierType)) {
      if (qualifier instanceof GrReferenceExpression) {
        PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
        if (resolved instanceof PsiPackage || resolved instanceof PsiVariable) {
          resolved.processDeclarations(myProcessor, state, null, myRefExpr);
          return;
        }
      }
      getVariantsFromQualifierType(TypesUtil.getJavaLangObject(qualifier), project);
    }
    else if (qualifierType instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)qualifierType).getConjuncts()) {
        getVariantsFromQualifierType(conjunct, project);
      }
    }
    else if (qualifierType instanceof GrTraitType) {
      // Process trait type conjuncts in reversed order because last applied trait matters.
      PsiType[] conjuncts = ((GrTraitType)qualifierType).getConjuncts();
      for (int i = conjuncts.length - 1; i >= 0; i--) {
        getVariantsFromQualifierType(conjuncts[i], project);
      }
    }
    else {
      getVariantsFromQualifierType(qualifierType, project);
      if (qualifier instanceof GrReferenceExpression && !PsiUtil.isSuperReference(qualifier) && !PsiUtil.isInstanceThisRef(qualifier)) {
        PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
        if (resolved instanceof PsiClass) { ////omitted .class
          GlobalSearchScope scope = myRefExpr.getResolveScope();
          PsiClass javaLangClass = PsiUtil.getJavaLangClass(resolved, scope);
          if (javaLangClass != null) {
            PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
            PsiTypeParameter[] typeParameters = javaLangClass.getTypeParameters();
            if (typeParameters.length == 1) {
              substitutor = substitutor.put(typeParameters[0], qualifierType);
            }
            PsiType javaLangClassType = JavaPsiFacade.getElementFactory(myRefExpr.getProject()).createType(javaLangClass, substitutor);
            ResolveUtil.processAllDeclarations(javaLangClassType, myProcessor, state, myRefExpr);
          }
        }
      }
    }
  }


  private void getVariantsFromQualifierType(@NotNull PsiType qualifierType,
                                            @NotNull Project project) {
    final ResolveState state = ResolveState.initial();
    if (qualifierType instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = ((PsiClassType)qualifierType).resolveGenerics();
      PsiClass qualifierClass = result.getElement();
      if (qualifierClass != null) {
        qualifierClass.processDeclarations(myProcessor, state.put(PsiSubstitutor.KEY, result.getSubstitutor()), null, myRefExpr);
      }
    }
    else if (qualifierType instanceof PsiArrayType) {
      final GrTypeDefinition arrayClass =
        GroovyPsiManager.getInstance(project).getArrayClass(((PsiArrayType)qualifierType).getComponentType());
      if (arrayClass != null) {
        if (!arrayClass.processDeclarations(myProcessor, state, null, myRefExpr)) return;
      }
    }
    else if (qualifierType instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)qualifierType).getConjuncts()) {
        getVariantsFromQualifierType(conjunct, project);
      }
      return;
    }
    ResolveUtil.processNonCodeMembers(qualifierType, myProcessor, myRefExpr, state);
  }

  @NotNull
  private Set<String> addAllRestrictedProperties() {
    if (myRefExpr.getQualifier() != null) {
      return Collections.emptySet();
    }

    Set<String> propertyNames = new HashSet<>();
    for (GrTypeDefinition containingClass = PsiTreeUtil.getParentOfType(myRefExpr, GrTypeDefinition.class);
         containingClass != null;
         containingClass = PsiTreeUtil.getParentOfType(containingClass, GrTypeDefinition.class)) {
      for (PsiField field : containingClass.getFields()) {
        propertyNames.add(field.getName());
      }
    }
    return propertyNames;
  }

  private boolean isMap() {
    final PsiType qType = PsiImplUtil.getQualifierType(myRefExpr);
    return InheritanceUtil.isInheritor(qType, CommonClassNames.JAVA_UTIL_MAP);
  }

  private class CompleteReferenceProcessor extends ResolverProcessorImpl implements Consumer<Object> {

    private final Consumer<LookupElement> myConsumer;

    private final boolean mySkipPackages;
    private final PsiClass myEventListener;
    private final boolean myMethodPointerOperator;
    private final boolean myFieldPointerOperator;
    private final boolean myIsMap;

    private final SubstitutorComputer mySubstitutorComputer;

    private final Collection<String> myPreferredFieldNames; //Reference is inside classes with such fields so don't suggest properties with such names.
    private final Set<String> myPropertyNames = new HashSet<>();
    private final Set<String> myLocalVars = new HashSet<>();
    private final Set<GrMethod> myProcessedMethodWithOptionalParams = new HashSet<>();

    private List<GroovyResolveResult> myInapplicable;

    private boolean myIsEmpty = true;

    protected CompleteReferenceProcessor() {
      super(null, EnumSet.allOf(DeclarationKind.class), myRefExpr, PsiType.EMPTY_ARRAY);
      myConsumer = element -> {
        myIsEmpty = false;
        CompleteReferenceExpression.this.myConsumer.consume(element);
      };
      myPreferredFieldNames = addAllRestrictedProperties();
      mySkipPackages = shouldSkipPackages();
      myEventListener = JavaPsiFacade.getInstance(myRefExpr.getProject()).findClass("java.util.EventListener", myRefExpr.getResolveScope());
      myPropertyNames.addAll(myPreferredFieldNames);

      myFieldPointerOperator = myRefExpr.hasAt();
      myMethodPointerOperator = myRefExpr.getDotTokenType() == GroovyTokenTypes.mMEMBER_POINTER;
      myIsMap = isMap();
      final PsiType thisType = PsiImplUtil.getQualifierType(myRefExpr);
      mySubstitutorComputer = new SubstitutorComputer(thisType, PsiType.EMPTY_ARRAY, PsiType.EMPTY_ARRAY, myRefExpr, myRefExpr.getParent());
    }

    public boolean isEmpty() {
      return myIsEmpty;
    }

    private boolean shouldSkipPackages() {
      if (PsiImplUtil.getRuntimeQualifier(myRefExpr) != null) {
        return false;
      }

      PsiElement parent = myRefExpr.getParent();
      return parent == null || parent.getLanguage().isKindOf(GroovyLanguage.INSTANCE); //don't skip in Play!
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
          substitutor = mySubstitutorComputer.obtainSubstitutor(substitutor, (PsiMethod)element, resolveContext);
        }

        consume(new GroovyResolveResultImpl(namedElement, resolveContext, spreadState, substitutor, isAccessible, isStaticsOK));
      }
      return true;
    }

    @Override
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
        GrMethod base = ((GrReflectedMethod)element).getBaseMethod();
        if (!myProcessedMethodWithOptionalParams.add(base)) return;

        result = PsiImplUtil.reflectedToBase(result, base, (GrReflectedMethod)element);
        element = base;
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

    private void processPropertyFromField(@NotNull GrField field, @NotNull GroovyResolveResult resolveResult) {
      if (field.getGetters().length != 0 || field.getSetter() != null || !myPropertyNames.add(field.getName()) || myIsMap) return;

      for (LookupElement element : GroovyCompletionUtil.createLookupElements(resolveResult, false, myMatcher, null)) {
        myConsumer.consume(((LookupElementBuilder)element).withIcon(JetgroovyIcons.Groovy.Property));
      }

    }

    private void processProperty(@NotNull PsiMethod method, @NotNull GroovyResolveResult resolveResult) {
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

    private void processListenerProperties(@NotNull PsiMethod method) {
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
      List<GroovyResolveResult> list = new ArrayList<>(results.length);
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

    @NotNull
    private List<GroovyResolveResult> getInapplicableResults() {
      if (myInapplicable == null) return Collections.emptyList();
      return myInapplicable;
    }
  }
}
