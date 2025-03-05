// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.completion.api.GroovyCompletionConsumer;
import org.jetbrains.plugins.groovy.lang.completion.api.GroovyCompletionCustomizer;
import org.jetbrains.plugins.groovy.lang.completion.impl.FastGroovyCompletionConsumer;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileBaseImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt;
import org.jetbrains.plugins.groovy.lang.typing.TypeUtils;
import org.jetbrains.plugins.groovy.refactoring.DefaultGroovyVariableNameValidator;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.refactoring.inline.InlineMethodConflictSolver;

import java.util.*;
import java.util.stream.Collectors;

import static org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil.canResolveToPackage;

public class GrMainCompletionProvider extends CompletionProvider<CompletionParameters> {
  public static final ElementPattern<PsiElement> AFTER_AT = PlatformPatterns.psiElement().afterLeaf("@");
  public static final ElementPattern<PsiElement> IN_CATCH_TYPE = PlatformPatterns
    .psiElement().afterLeaf(PlatformPatterns.psiElement().withText("(").withParent(GrCatchClause.class));

  private static void addUnfinishedMethodTypeParameters(@NotNull PsiElement position, @NotNull GroovyCompletionConsumer result) {
    final GrTypeParameterList candidate = findTypeParameterListCandidate(position);

    if (candidate != null) {
      for (GrTypeParameter p : candidate.getTypeParameters()) {
        result.consume(new JavaPsiClassReferenceElement(p));
      }
    }
  }

  private static void suggestVariableNames(PsiElement context, GroovyCompletionConsumer result) {
    final PsiElement parent = context.getParent();
    if (GroovyCompletionUtil.isWildcardCompletion(context)) return;
    if (parent instanceof GrVariable variable) {
      if (context.equals(variable.getNameIdentifierGroovy())) {
        final PsiType type = variable.getTypeGroovy();
        if (type != null) {
          final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(context.getProject());
          VariableKind kind = variable instanceof GrParameter ? VariableKind.PARAMETER :
              variable instanceof GrField ? VariableKind.FIELD : VariableKind.LOCAL_VARIABLE;
          SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(kind, null, null, type);
          String[] names = suggestedNameInfo.names;
          if (names.length > 0) {
            String name = names[0];
            String newName = InlineMethodConflictSolver.suggestNewName(name, null, parent);
            if (!name.equals(newName)) {
              result.consume(LookupElementBuilder.create(newName));
              return;
            }
          }
          for (String name : names) {
            result.consume(LookupElementBuilder.create(name));
          }
        }

        GrExpression initializer = variable.getInitializerGroovy();
        if (initializer != null) {
          for (String name : GroovyNameSuggestionUtil.suggestVariableNames(initializer, new DefaultGroovyVariableNameValidator(variable),
                                                                           variable.hasModifierProperty(PsiModifier.STATIC))) {
            result.consume(LookupElementBuilder.create(name));
          }
        }
      }
    }
  }

  private static @Nullable GrReferenceElement<?> findGroovyReference(@NotNull PsiElement position) {
    final PsiElement parent = position.getParent();
    if (parent instanceof GrReferenceElement) {
      return (GrReferenceElement<?>)parent;
    }
    if (couldContainReference(position)) {
      return GroovyPsiElementFactory.getInstance(position.getProject()).createCodeReference("Foo", position);
    }
    return null;
  }

  private static boolean couldContainReference(PsiElement position) {
    return IN_CATCH_TYPE.accepts(position) ||
           AFTER_AT.accepts(position) ||
           GroovyCompletionUtil.isFirstElementAfterPossibleModifiersInVariableDeclaration(position, true) ||
           GroovyCompletionUtil.isTupleVarNameWithoutTypeDeclared(position);
  }

  private static @Nullable GrTypeParameterList findTypeParameterListCandidate(@NotNull PsiElement position) {
    final PsiElement parent = position.getParent();
    if (parent instanceof GrVariable) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof GrVariableDeclaration) {
        PsiElement candidate = PsiUtil.skipWhitespacesAndComments(parent.getPrevSibling(), false);
        if (candidate instanceof GrTypeParameterList) {
          return (GrTypeParameterList)candidate;
        }
      }
    }
    return null;
  }

  public static boolean isClassNamePossible(PsiElement position) {
    PsiElement parent = position.getParent();
    if (parent instanceof GrReferenceElement) {
      return ((GrReferenceElement<?>)parent).getQualifier() == null;
    }
    return couldContainReference(position);
  }

  private static void addAllClasses(CompletionParameters parameters, final GroovyCompletionConsumer result, final JavaCompletionSession session) {
    addAllClasses(parameters, result::consume, session, result.getCompletionResultSet().getPrefixMatcher());
  }

  public static void addAllClasses(CompletionParameters parameters,
                                   final Consumer<? super LookupElement> consumer,
                                   final JavaCompletionSession inheritors, final PrefixMatcher matcher) {
    final PsiElement position = parameters.getPosition();
    final boolean afterNew = JavaClassNameCompletionContributor.AFTER_NEW.accepts(position);
    AllClassesGetter.processJavaClasses(parameters, matcher, parameters.getInvocationCount() <= 1, psiClass -> {
      for (JavaPsiClassReferenceElement element : JavaClassNameCompletionContributor
        .createClassLookupItems(psiClass, afterNew, new GroovyClassNameInsertHandler(),
                                psiClass1 -> !inheritors.alreadyProcessed(psiClass1))) {
        consumer.consume(element);
      }
    });
  }

  static @NotNull Runnable completeReference(final CompletionParameters parameters,
                                             final GrReferenceElement<?> reference,
                                             final JavaCompletionSession inheritorsHolder,
                                             final PrefixMatcher matcher,
                                             final @Nullable CompletionResultSet resultSet,
                                             final Consumer<? super LookupElement> _consumer) {
    final HashSet<LookupElement> addedElements = new HashSet<>();
    final Consumer<LookupElement> consumer = new Consumer<>() {
      final Set<LookupElement> added = addedElements;

      @Override
      public void consume(LookupElement element) {
        if (added.add(element)) {
          _consumer.consume(element);
        }
      }
    };

    final Map<PsiModifierListOwner, LookupElement> staticMembers = new HashMap<>();
    final PsiElement qualifier = reference.getQualifier();
    final PsiType qualifierType = GroovyCompletionUtil.getQualifierType(qualifier);

    if (reference instanceof GrReferenceExpression && (qualifier instanceof GrExpression || qualifier == null)) {
      for (String string : CompleteReferencesWithSameQualifier.getVariantsWithSameQualifier((GrReferenceExpression)reference, matcher, (GrExpression)qualifier)) {
        consumer.consume(LookupElementBuilder.create(string).withItemTextUnderlined(true));
      }
      if (parameters.getInvocationCount() < 2 && qualifier != null && qualifierType == null && !canResolveToPackage(qualifier)) {
        if (resultSet != null && parameters.getInvocationCount() == 1) {
          resultSet.addLookupAdvertisement(GroovyBundle.message("invoke.completion.second.time.to.show.skipped.methods"));
        }
        return EmptyRunnable.INSTANCE;
      }
    }

    final List<LookupElement> zeroPriority = new ArrayList<>();

    PsiClass qualifierClass = com.intellij.psi.util.PsiUtil.resolveClassInClassTypeOnly(qualifierType);
    final boolean honorExcludes = qualifierClass == null || !JavaCompletionUtil.isInExcludedPackage(qualifierClass, false);

    Consumer<LookupElement> elementConsumer = lookupElement -> {
      Object object = lookupElement.getObject();
      if (object instanceof GroovyResolveResult) {
        object = ((GroovyResolveResult)object).getElement();
      }

      if (isLightElementDeclaredDuringCompletion(object)) {
        return;
      }

      if (!(lookupElement instanceof LookupElementBuilder) && inheritorsHolder.alreadyProcessed(lookupElement)) {
        return;
      }

      if (honorExcludes && object instanceof PsiMember && JavaCompletionUtil.isInExcludedPackage((PsiMember)object, true)) {
        return;
      }

      if (!(object instanceof PsiClass)) {
        int priority = assignPriority(lookupElement, qualifierType);
        lookupElement = PrioritizedLookupElement.withPriority(lookupElement, priority);
        if (object != null) {
          lookupElement = JavaCompletionUtil.highlightIfNeeded(qualifierType, parameters.getOriginalFile().getVirtualFile(), lookupElement, object, reference);
        }
      }

      if ((object instanceof PsiMethod || object instanceof PsiField) &&
          ((PsiModifierListOwner)object).hasModifierProperty(PsiModifier.STATIC)) {
        if (lookupElement.getLookupString().equals(((PsiMember)object).getName())) {
          staticMembers.put(CompletionUtil.getOriginalOrSelf((PsiModifierListOwner)object), lookupElement);
        }
      }

      PrioritizedLookupElement<?> prioritized = lookupElement.as(PrioritizedLookupElement.CLASS_CONDITION_KEY);
      if (prioritized == null || prioritized.getPriority() == 0) {
        zeroPriority.add(lookupElement);
      }
      else {
        consumer.consume(lookupElement);
      }
    };

    GroovyCompletionUtil.processVariants(reference, matcher, parameters, elementConsumer);

    for (LookupElement element : zeroPriority) {
      consumer.consume(element);
    }
    zeroPriority.clear();

    if (qualifierType != null) {
      processImplicitSpread(qualifierType, consumer, matcher, addedElements);
    }

    if (qualifier == null) {
      return () -> {
        PsiFile file = reference.getContainingFile();
        if (!(file instanceof GroovyFileBaseImpl) || !(reference instanceof GrReferenceExpressionImpl)) {
          return;
        }
        CompleteReferenceExpression.processSpecificPlace(matcher, (GrReferenceExpressionImpl)reference, parameters, (processor) -> ((GroovyFileBaseImpl)file).processComplexImports(processor, ResolveUtilKt.initialState(true), reference), elementConsumer);
        for (LookupElement element : zeroPriority) {
          consumer.consume(element);
        }
        addStaticMembers(parameters, matcher, staticMembers, consumer).run();
      };
    }
    return EmptyRunnable.INSTANCE;
  }

  private static void processImplicitSpread(@NotNull PsiType type,
                                            @NotNull Consumer<LookupElement> consumer,
                                            @NotNull PrefixMatcher matcher,
                                            @NotNull HashSet<@NotNull LookupElement> addedElements) {
    var componentPair = PsiUtil.getComponentForSpreadWithDot(type);
    if (componentPair == null) {
      return;
    }
    PsiType deepComponentType = componentPair.first;
    int depth = componentPair.second;
    if (!(deepComponentType instanceof PsiClassType)) {
      return;
    }
    var resolveResult = ((PsiClassType)deepComponentType).resolveGenerics();
    PsiClass resolvedClass = resolveResult.getElement();
    if (resolvedClass == null) {
      return;
    }
    Set<String> existingIdentifiers = addedElements.stream().map(element -> element.getLookupString()).collect(Collectors.toSet());
    for (var method : resolvedClass.getAllMethods()) {
      if (GroovyPropertyUtils.isSimplePropertyGetter(method)) {
        var lookupElement = CompleteReferenceExpression.createPropertyLookupElement(method, resolveResult.getSubstitutor(), matcher);
        if (lookupElement != null && !existingIdentifiers.contains(lookupElement.getLookupString())) {
          PsiType methodReturnType = resolveResult.getSubstitutor().substitute(method.getReturnType());
          String returnTypeRepresentation = methodReturnType == null ? "?" : TypeUtils.box(methodReturnType, method).getPresentableText();
          consumer.consume(lookupElement.withTypeText(StringUtil.repeat("ArrayList<", depth) + returnTypeRepresentation + StringUtil.repeat(">", depth)));
        }
      }
    }
  }

  private static boolean isLightElementDeclaredDuringCompletion(Object object) {
    if (!(object instanceof LightElement && object instanceof PsiNamedElement)) return false;
    final String name = ((PsiNamedElement)object).getName();
    if (name == null) return false;

    return name.contains(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED.trim()) ||
           name.contains(GrDummyIdentifierProvider.DUMMY_IDENTIFIER_DECAPITALIZED.trim());
  }

  private static Runnable addStaticMembers(CompletionParameters parameters,
                                       final PrefixMatcher matcher,
                                       final Map<PsiModifierListOwner, LookupElement> staticMembers, final Consumer<? super LookupElement> consumer) {
    final StaticMemberProcessor processor = completeStaticMembers(parameters);
    processor.processMembersOfRegisteredClasses(matcher::prefixMatches, (member, psiClass) -> {
      if (member instanceof GrAccessorMethod) {
        member = ((GrAccessorMethod)member).getProperty();
      }
      member = CompletionUtil.getOriginalOrSelf(member);
      if (staticMembers.containsKey(member)) {
        return;
      }
      final String name = member.getName();
      if (name == null || !matcher.prefixMatches(name)) {
        staticMembers.remove(member);
        return;
      }
      JavaGlobalMemberLookupElement element = createGlobalMemberElement(member, psiClass, true);
      staticMembers.put(member, element);
      consumer.consume(element);
    });
    if (parameters.getInvocationCount() >= 2 && StringUtil.isNotEmpty(matcher.getPrefix())) {
      return () -> processor.processStaticMethodsGlobally(matcher, element -> {
        PsiMember member = (PsiMember)element.getObject();
        if (member instanceof GrAccessorMethod) {
          member = ((GrAccessorMethod)member).getProperty();
        }
        member = CompletionUtil.getOriginalOrSelf(member);
        if (staticMembers.containsKey(member)) {
          return;
        }
        staticMembers.put(member, element);
        consumer.consume(element);
      });
    }
    return EmptyRunnable.INSTANCE;
  }

  private static boolean checkForIterator(PsiMethod method) {
    if (!"next".equals(method.getName())) return false;

    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return false;
    final PsiClass iterator = JavaPsiFacade.getInstance(method.getProject()).findClass(CommonClassNames.JAVA_UTIL_ITERATOR,
                                                                                       method.getResolveScope());
    return InheritanceUtil.isInheritorOrSelf(containingClass, iterator, true);
  }

  private static int assignPriority(LookupElement lookupElement, PsiType qualifierType) {
    Object object = lookupElement.getObject();
    PsiSubstitutor substitutor = null;
    GroovyResolveResult resolveResult = null;
    if (object instanceof GroovyResolveResult) {
      resolveResult = (GroovyResolveResult)object;
      substitutor = resolveResult.getSubstitutor();
      object = ((GroovyResolveResult)object).getElement();
    }

    // default groovy methods
    if (object instanceof GrGdkMethod &&
        GroovyCompletionUtil.skipDefGroovyMethod((GrGdkMethod)object, substitutor, qualifierType)) {
      return -1;
    }

    // operator methods
    if (object instanceof PsiMethod &&
        PsiUtil.OPERATOR_METHOD_NAMES.contains(((PsiMethod)object).getName()) && !checkForIterator((PsiMethod)object)) {
      return -3;
    }

    // accessors if there is no get, set, is prefix
    if (object instanceof PsiMethod && GroovyPropertyUtils.isSimplePropertyAccessor((PsiMethod)object)) {
      return -1;
    }

    // inaccessible elements
    if (resolveResult != null && !resolveResult.isAccessible()) {
      return -2;
    }
    return 0;
  }

  static StaticMemberProcessor completeStaticMembers(CompletionParameters parameters) {
    final PsiElement position = parameters.getPosition();
    final PsiElement originalPosition = parameters.getOriginalPosition();
    final StaticMemberProcessor processor = new StaticMemberProcessor(position) {
      @Override
      protected @NotNull LookupElement createLookupElement(@NotNull PsiMember member, @NotNull PsiClass containingClass, boolean shouldImport) {
        shouldImport |= originalPosition != null && PsiTreeUtil.isAncestor(containingClass, originalPosition, false);
        return createGlobalMemberElement(member, containingClass, shouldImport);
      }

      @Override
      protected LookupElement createLookupElement(@NotNull List<? extends PsiMethod> overloads,
                                                  @NotNull PsiClass containingClass,
                                                  boolean shouldImport) {
        shouldImport |= originalPosition != null && PsiTreeUtil.isAncestor(containingClass, originalPosition, false);
        return new JavaGlobalMemberLookupElement(overloads, containingClass, QualifiedMethodInsertHandler.INSTANCE, StaticImportInsertHandler.INSTANCE, shouldImport);
      }

      @Override
      protected boolean isAccessible(@NotNull PsiMember member) {
        boolean result = super.isAccessible(member);

        if (!result && member instanceof GrField) {
          GrAccessorMethod[] getters = ((GrField)member).getGetters();
          return getters.length > 0 && super.isAccessible(getters[0]);
        }

        return result;
      }
    };
    final PsiFile file = position.getContainingFile();
    if (file instanceof GroovyFile) {
      for (GrImportStatement statement : ((GroovyFile)file).getImportStatements()) {
        if (statement.isStatic()) {
          GrCodeReferenceElement importReference = statement.getImportReference();
          if (importReference != null) {
            if (!statement.isOnDemand()) {
              importReference = importReference.getQualifier();
            }
            if (importReference != null) {
              final PsiElement target = importReference.resolve();
              if (target instanceof PsiClass) {
                processor.importMembersOf((PsiClass)target);
              }
            }
          }
        }
      }
    }
    return processor;
  }

  static JavaGlobalMemberLookupElement createGlobalMemberElement(PsiMember member, PsiClass containingClass, boolean shouldImport) {
    return new JavaGlobalMemberLookupElement(member, containingClass, QualifiedMethodInsertHandler.INSTANCE, StaticImportInsertHandler.INSTANCE, shouldImport);
  }

  public static void register(CompletionContributor contributor) {
    contributor.extend(CompletionType.BASIC, PlatformPatterns.psiElement(PsiElement.class), new GrMainCompletionProvider());
  }

  @Override
  protected final void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      final @NotNull CompletionResultSet result) {

    try (GroovyCompletionConsumer consumer = getCompletionConsumer(result, parameters)) {
      doAddCompletions(parameters, consumer);
    } catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void doAddCompletions(CompletionParameters parameters, GroovyCompletionConsumer consumer) {

    GroovyCompletionData.addGroovyDocKeywords(parameters, consumer);

    PsiElement position = parameters.getPosition();
    if (PlatformPatterns.psiElement().inside(false, PlatformPatterns.psiElement(PsiComment.class)).accepts(position)) {
      return;
    }

    GroovyCompletionData.addGroovyKeywords(parameters, consumer);

    addUnfinishedMethodTypeParameters(position, consumer);

    suggestVariableNames(position, consumer);

    GrReferenceElement<?> reference = findGroovyReference(position);
    if (reference == null) {
      if (parameters.getInvocationCount() >= 2) {
        consumer.interrupt();
        addAllClasses(parameters, consumer.transform(crs -> crs.withPrefixMatcher(CompletionUtil.findJavaIdentifierPrefix(parameters))), new JavaCompletionSession(consumer.getCompletionResultSet()));
      }
      return;
    }

    if (reference.getParent() instanceof GrImportStatement && reference.getQualifier() != null) {
      consumer.consume(LookupElementBuilder.create("*"));
    }

    JavaCompletionSession inheritors = new JavaCompletionSession(consumer.getCompletionResultSet());
    if (GroovySmartCompletionContributor.AFTER_NEW.accepts(position)) {
      GroovySmartCompletionContributor.generateInheritorVariants(parameters, consumer.getCompletionResultSet().getPrefixMatcher(), inheritors::addClassItem);
    }

    Runnable addSlowVariants = completeReference(
      parameters, reference, inheritors, consumer.getCompletionResultSet().getPrefixMatcher(), consumer.getCompletionResultSet(),
      lookupElement -> consumer.consume(lookupElement)
    );

    if (reference.getQualifier() == null) {
      if (!GroovySmartCompletionContributor.AFTER_NEW.accepts(position)) {
        GroovySmartCompletionContributor.addExpectedClassMembers(parameters, consumer);
      }

      if (isClassNamePossible(position) && JavaCompletionContributor.mayStartClassName(consumer.getCompletionResultSet())) {
        //consumer.interrupt();
        if (parameters.getInvocationCount() >= 2) {
          addAllClasses(parameters, consumer, inheritors);
        } else {
          JavaCompletionContributor.advertiseSecondCompletion(position.getProject(), consumer.getCompletionResultSet());
        }
      }
    }
    consumer.fastElementsProcessed(parameters);
    if (GroovyCompletionUtil.isSlowCompletionEnabled()) {
      addSlowVariants.run();
    }
  }

  private static GroovyCompletionConsumer getCompletionConsumer(CompletionResultSet resultSet, CompletionParameters completionParameters) {
    PsiElement position = completionParameters.getPosition();
    for (GroovyCompletionCustomizer customizer : GroovyCompletionContributor.EP_NAME.getExtensionList()) {
      GroovyCompletionConsumer consumer = customizer.generateCompletionConsumer(position, resultSet);
      if (consumer != null) {
        return consumer;
      }
    }
    return new FastGroovyCompletionConsumer(resultSet);
  }
}
