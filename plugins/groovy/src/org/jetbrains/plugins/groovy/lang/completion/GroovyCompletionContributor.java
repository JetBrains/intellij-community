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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.ProcessingContext;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.CompleteReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.DefaultGroovyVariableNameValidator;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.refactoring.inline.InlineMethodConflictSolver;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.elementType;
import static com.intellij.util.containers.ContainerUtil.*;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.SEPARATORS;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.WHITE_SPACES_OR_COMMENTS;

/**
 * @author ilyas
 */
public class GroovyCompletionContributor extends CompletionContributor {
  private static final ElementPattern<PsiElement> AFTER_DOT = psiElement().afterLeaf(".").withParent(GrReferenceExpression.class);

  private static final String[] THIS_SUPER = {"this", "super"};
  private static final InsertHandler<JavaGlobalMemberLookupElement> STATIC_IMPORT_INSERT_HANDLER = new InsertHandler<JavaGlobalMemberLookupElement>() {
    @Override
    public void handleInsert(InsertionContext context, JavaGlobalMemberLookupElement item) {
      GroovyInsertHandler.INSTANCE.handleInsert(context, item);
      final PsiMember member = item.getObject();
      PsiDocumentManager.getInstance(member.getProject()).commitDocument(context.getDocument());
      final GrReferenceExpression ref = PsiTreeUtil.
        findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), GrReferenceExpression.class, false);

      if (ref != null &&
          ref.getQualifier() == null &&
          context.getFile() instanceof GroovyFile &&
          !importAlreadyExists(member, ((GroovyFile)context.getFile()), ref) &&
          !PsiManager.getInstance(context.getProject()).areElementsEquivalent(ref.resolve(), member)) {
        ref.bindToElementViaStaticImport(member);
      }

    }
  };

  private static boolean importAlreadyExists(final PsiMember member, final GroovyFile file, final PsiElement place) {
    final PsiManager manager = file.getManager();
    PsiScopeProcessor processor = new PsiScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, ResolveState state) {
        return !manager.areElementsEquivalent(element, member);
      }

      @Override
      public <T> T getHint(@NotNull Key<T> hintKey) {
        return null;
      }

      @Override
      public void handleEvent(Event event, Object associated) {
      }
    };

    boolean skipStaticImports = member instanceof PsiClass;
    final GrImportStatement[] imports = file.getImportStatements();
    final ResolveState initial = ResolveState.initial();
    for (GrImportStatement anImport : imports) {
      if (skipStaticImports == anImport.isStatic()) continue;
      if (!anImport.processDeclarations(processor, initial, null, place)) return true;
    }
    return false;
  }


  private static final InsertHandler<JavaGlobalMemberLookupElement> QUALIFIED_METHOD_INSERT_HANDLER = new InsertHandler<JavaGlobalMemberLookupElement>() {
    @Override
    public void handleInsert(InsertionContext context, JavaGlobalMemberLookupElement item) {
      GroovyInsertHandler.INSTANCE.handleInsert(context, item);
      final PsiClass containingClass = item.getContainingClass();
      context.getDocument().insertString(context.getStartOffset(), containingClass.getName() + ".");
      PsiDocumentManager.getInstance(containingClass.getProject()).commitDocument(context.getDocument());
      final GrReferenceExpression ref = PsiTreeUtil
        .findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), GrReferenceExpression.class, false);
      if (ref != null) {
        ref.bindToElement(containingClass);
      }
    }
  };

  private static final PsiElementPattern.Capture<PsiElement> STATEMENT_START =
    psiElement(mIDENT).andOr(
      psiElement().afterLeaf(StandardPatterns.or(
        psiElement().isNull(),
        psiElement().withElementType(SEPARATORS),
        psiElement(mLCURLY),
        psiElement(kELSE)
      )).andNot(psiElement().withParent(GrTypeDefinitionBody.class))
        .andNot(psiElement(PsiErrorElement.class)),
      psiElement().afterLeaf(psiElement(mRPAREN)).withSuperParent(2, StandardPatterns.or(
        psiElement(GrForStatement.class),
        psiElement(GrWhileStatement.class),
        psiElement(GrIfStatement.class)
      ))
    );

  private static final ElementPattern<PsiElement> AFTER_NUMBER_LITERAL = psiElement().afterLeaf(
    psiElement().withElementType(elementType().oneOf(mNUM_DOUBLE, mNUM_INT, mNUM_LONG, mNUM_FLOAT, mNUM_BIG_INT, mNUM_BIG_DECIMAL)));
  public static final ElementPattern<PsiElement> AFTER_AT = psiElement().afterLeaf("@");
  public static final ElementPattern<PsiElement> IN_CATCH_TYPE = psiElement().afterLeaf(psiElement().withText("(").withParent(GrCatchClause.class));


  private static void addAllClasses(CompletionParameters parameters, final CompletionResultSet result, final InheritorsHolder inheritors) {
    addAllClasses(parameters, new Consumer<LookupElement>() {
      @Override
      public void consume(LookupElement element) {
        result.addElement(element);
      }
    }, inheritors, result.getPrefixMatcher());
  }

  public static void addAllClasses(CompletionParameters parameters,
                                   final Consumer<LookupElement> consumer,
                                   final InheritorsHolder inheritors, final PrefixMatcher matcher) {
    final PsiElement position = parameters.getPosition();
    final boolean afterNew = JavaClassNameCompletionContributor.AFTER_NEW.accepts(position);
    AllClassesGetter.processJavaClasses(parameters, matcher, parameters.getInvocationCount() <= 1, new Consumer<PsiClass>() {
      @Override
      public void consume(PsiClass psiClass) {
        for (JavaPsiClassReferenceElement element : JavaClassNameCompletionContributor
          .createClassLookupItems(psiClass, afterNew, new GroovyClassNameInsertHandler(), new Condition<PsiClass>() {
            @Override
            public boolean value(PsiClass psiClass) {
              return !inheritors.alreadyProcessed(psiClass);
            }
          })) {
          consumer.consume(element);
        }
      }
    });
  }

  public GroovyCompletionContributor() {
    //provide 'this' and 'super' completions in ClassName.<caret>
    extend(CompletionType.BASIC, AFTER_DOT, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();

        assert position.getParent() instanceof GrReferenceExpression;
        final GrReferenceExpression refExpr = ((GrReferenceExpression)position.getParent());
        final GrExpression qualifier = refExpr.getQualifierExpression();
        if (!(qualifier instanceof GrReferenceExpression)) return;

        GrReferenceExpression referenceExpression = (GrReferenceExpression)qualifier;
        final PsiElement resolved = referenceExpression.resolve();
        if (!(resolved instanceof PsiClass)) return;

        if (parameters.getInvocationCount() > 0 &&
            CompletionUtil.shouldShowFeature(parameters, JavaCompletionFeatures.GLOBAL_MEMBER_NAME)) {
          final String shortcut = getActionShortcut(IdeActions.ACTION_CODE_COMPLETION);
          if (shortcut != null) {
            result.addLookupAdvertisement("Pressing " + shortcut + " twice without a class qualifier would show all accessible static methods");
          }
        }

        if (!PsiUtil.hasEnclosingInstanceInScope((PsiClass)resolved, position, false)) return;

        for (String keyword : THIS_SUPER) {
          result.addElement(LookupElementBuilder.create(keyword));
        }
      }
    });

    MapArgumentCompletionProvider.register(this);
    GroovyConfigSlurperCompletionProvider.register(this);
    MapKeysCompletionProvider.register(this);
    GroovyDocCompletionProvider.register(this);

    extend(CompletionType.BASIC, STATEMENT_START, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        result.addElement(LookupElementBuilder.create("if").bold().withInsertHandler(new InsertHandler<LookupElement>() {
          @Override
          public void handleInsert(InsertionContext context, LookupElement item) {
            if (context.getCompletionChar() != ' ') {
              TailTypes.IF_LPARENTH.processTail(context.getEditor(), context.getTailOffset());
            }
            if (context.getCompletionChar() == '(') {
              context.setAddCompletionChar(false);
            }
          }
        }));
      }
    });

    extend(CompletionType.BASIC, psiElement(PsiElement.class), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull final CompletionResultSet result) {
        GroovyCompletionData.addGroovyDocKeywords(parameters, result);

        PsiElement position = parameters.getPosition();
        if (psiElement().inside(false, psiElement(PsiComment.class)).accepts(position)) {
          return;
        }

        GroovyCompletionData.addGroovyKeywords(parameters, result);

        addUnfinishedMethodTypeParameters(position, result);

        suggestVariableNames(position, result);

        GrReferenceElement reference = findGroovyReference(position);
        if (reference == null) {
          if (parameters.getInvocationCount() >= 2) {
            result.stopHere();
            addAllClasses(parameters, result.withPrefixMatcher(CompletionUtil.findJavaIdentifierPrefix(parameters)), new InheritorsHolder(parameters.getPosition(), result));
          }
          return;
        }

        if (reference.getParent() instanceof GrImportStatement && reference.getQualifier() != null) {
          result.addElement(LookupElementBuilder.create("*"));
        }

        InheritorsHolder inheritors = new InheritorsHolder(position, result);
        if (GroovySmartCompletionContributor.AFTER_NEW.accepts(position)) {
          GroovySmartCompletionContributor.generateInheritorVariants(parameters, result.getPrefixMatcher(), inheritors);
        }

        Runnable addSlowVariants = completeReference(parameters, reference, inheritors, result.getPrefixMatcher(), new Consumer<LookupElement>() {
          @Override
          public void consume(LookupElement lookupElement) {
            result.addElement(lookupElement);
          }
        });

        if (reference.getQualifier() == null) {
          if (!GroovySmartCompletionContributor.AFTER_NEW.accepts(position)) {
            GroovySmartCompletionContributor.addExpectedClassMembers(parameters, result);
          }

          if (isClassNamePossible(position) && JavaCompletionContributor.mayStartClassName(result)) {
            result.stopHere();
            if (parameters.getInvocationCount() >= 2) {
              addAllClasses(parameters, result, inheritors);
            } else {
              JavaCompletionContributor.advertiseSecondCompletion(position.getProject(), result);
            }
          }
        }

        addSlowVariants.run();
      }
    });

    extend(CompletionType.BASIC, psiElement().withParent(GrLiteral.class), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull final CompletionResultSet result) {
        final Set<String> usedWords = new THashSet<String>();
        for (CompletionResult element : result.runRemainingContributors(parameters, true)) {
          usedWords.add(element.getLookupElement().getLookupString());
        }

        PsiReference reference = parameters.getPosition().getContainingFile().findReferenceAt(parameters.getOffset());
        if (reference == null || reference.isSoft()) {
          WordCompletionContributor.addWordCompletionVariants(result, parameters, usedWords);
        }
      }
    });

  }

  @Nullable private static GrReferenceElement findGroovyReference(PsiElement position) {
    final PsiElement parent = position.getParent();
    if (parent instanceof GrReferenceElement) {
      return (GrReferenceElement)parent;
    }
    if (couldContainReference(position)) {
      return GroovyPsiElementFactory.getInstance(position.getProject()).createReferenceElementFromText("Foo", position);
    }
    return null;
  }

  private static boolean couldContainReference(PsiElement position) {
    return IN_CATCH_TYPE.accepts(position) ||
           AFTER_AT.accepts(position) ||
           GroovyCompletionUtil.isFirstElementAfterPossibleModifiersInVariableDeclaration(position, true) ||
           GroovyCompletionUtil.isTupleVarNameWithoutTypeDeclared(position);
  }

  public static boolean isClassNamePossible(PsiElement position) {
    PsiElement parent = position.getParent();
    if (parent instanceof GrReferenceElement) {
      return ((GrReferenceElement)parent).getQualifier() == null;
    }
    return couldContainReference(position);
  }

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
    if (AFTER_NUMBER_LITERAL.accepts(parameters.getPosition())) {
      return;
    }

    super.fillCompletionVariants(parameters, result);
  }


  private static void addUnfinishedMethodTypeParameters(@NotNull PsiElement position, @NotNull CompletionResultSet result) {
    final GrTypeParameterList candidate = findTypeParameterListCandidate(position);

    if (candidate != null) {
      for (GrTypeParameter p : candidate.getTypeParameters()) {
        result.addElement(new JavaPsiClassReferenceElement(p));
      }
    }
  }

  @Nullable
  private static GrTypeParameterList findTypeParameterListCandidate(@NotNull PsiElement position) {
    final PsiElement parent = position.getParent();
    if (parent instanceof GrVariable) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof GrVariableDeclaration) {
        final PsiElement errorElement = PsiUtil.skipWhitespacesAndComments(parent.getPrevSibling(), false);
        if (errorElement instanceof PsiErrorElement) {
          final PsiElement child = errorElement.getFirstChild();
          if (child instanceof GrTypeParameterList) {
            return (GrTypeParameterList)child;
          }
        }
      }
    }
    return null;
  }

  @NotNull
  static Runnable completeReference(final CompletionParameters parameters,
                                    final GrReferenceElement reference,
                                    final InheritorsHolder inheritorsHolder,
                                    final PrefixMatcher matcher,
                                    final Consumer<LookupElement> _consumer) {
    final Consumer<LookupElement> consumer = new Consumer<LookupElement>() {
      final Set<LookupElement> added = newHashSet();
      @Override
      public void consume(LookupElement element) {
        if (added.add(element)) {
          _consumer.consume(element);
        }
      }
    };

    final Map<PsiModifierListOwner, LookupElement> staticMembers = newHashMap();
    final PsiElement qualifier = reference.getQualifier();
    final PsiType qualifierType = qualifier instanceof GrExpression ? ((GrExpression)qualifier).getType() : null;

    if (reference instanceof GrReferenceExpression && (qualifier instanceof GrExpression || qualifier == null)) {
      for (String string : CompleteReferenceExpression.getVariantsWithSameQualifier(matcher, (GrExpression)qualifier,
                                                                                                   (GrReferenceExpression)reference)) {
        consumer.consume(LookupElementBuilder.create(string).withItemTextUnderlined(true));
      }
      if (parameters.getInvocationCount() < 2 && qualifier != null && qualifierType == null &&
          !(qualifier instanceof GrReferenceExpression && ((GrReferenceExpression)qualifier).resolve() instanceof PsiPackage)) {
        if (parameters.getInvocationCount() == 1) {
          showInfo();
        }
        return EmptyRunnable.INSTANCE;
      }
    }

    final List<LookupElement> zeroPriority = newArrayList();
    reference.processVariants(matcher, parameters, new Consumer<LookupElement>() {
      public void consume(LookupElement lookupElement) {
        Object object = lookupElement.getObject();
        if (object instanceof GroovyResolveResult) {
          object = ((GroovyResolveResult)object).getElement();
        }

        if (!(lookupElement instanceof LookupElementBuilder) && inheritorsHolder.alreadyProcessed(lookupElement)) {
          return;
        }

        if (object instanceof PsiMember && JavaCompletionUtil.isInExcludedPackage((PsiMember)object, true)) {
          return;
        }

        if (!(object instanceof PsiClass)) {
          int priority = assignPriority(lookupElement, qualifierType);
          lookupElement = JavaCompletionUtil.highlightIfNeeded(qualifierType,
                                                               PrioritizedLookupElement.withPriority(lookupElement, priority), object);
        }

        if ((object instanceof PsiMethod || object instanceof PsiField) &&
            ((PsiModifierListOwner)object).hasModifierProperty(PsiModifier.STATIC)) {
          if (lookupElement.getLookupString().equals(((PsiMember)object).getName())) {
            staticMembers.put(CompletionUtil.getOriginalOrSelf((PsiModifierListOwner)object), lookupElement);
          }
        }

        PrioritizedLookupElement prio = lookupElement.as(PrioritizedLookupElement.CLASS_CONDITION_KEY);
        if (prio == null || prio.getPriority() == 0) {
          zeroPriority.add(lookupElement);
        } else {
          consumer.consume(lookupElement);
        }
      }
    });

    for (LookupElement element : zeroPriority) {
      consumer.consume(element);
    }

    if (qualifier == null) {
      return addStaticMembers(parameters, matcher, staticMembers, consumer);
    }
    return EmptyRunnable.INSTANCE;
  }

  private static Runnable addStaticMembers(CompletionParameters parameters,
                                       final PrefixMatcher matcher,
                                       final Map<PsiModifierListOwner, LookupElement> staticMembers, final Consumer<LookupElement> consumer) {
    final StaticMemberProcessor processor = completeStaticMembers(parameters);
    processor.processMembersOfRegisteredClasses(matcher, new PairConsumer<PsiMember, PsiClass>() {
      @Override
      public void consume(PsiMember member, PsiClass psiClass) {
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
      }
    });
    if (parameters.getInvocationCount() >= 2 && StringUtil.isNotEmpty(matcher.getPrefix())) {
      return new Runnable() {
        @Override
        public void run() {
          processor.processStaticMethodsGlobally(matcher, new Consumer<LookupElement>() {
            @Override
            public void consume(LookupElement element) {
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
            }
          });
        }
      };
    }
    return EmptyRunnable.INSTANCE;
  }

  private static void showInfo() {
    CompletionService.getCompletionService()
      .setAdvertisementText(GroovyBundle.message("invoke.completion.second.time.to.show.skipped.methods"));
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
        GroovyCompletionUtil.OPERATOR_METHOD_NAMES.contains(((PsiMethod)object).getName()) && !checkForIterator((PsiMethod)object)) {
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
      @NotNull
      @Override
      protected LookupElement createLookupElement(@NotNull PsiMember member, @NotNull PsiClass containingClass, boolean shouldImport) {
        shouldImport |= originalPosition != null && PsiTreeUtil.isAncestor(containingClass, originalPosition, false);
        return createGlobalMemberElement(member, containingClass, shouldImport);
      }

      @Override
      protected LookupElement createLookupElement(@NotNull List<PsiMethod> overloads,
                                                  @NotNull PsiClass containingClass,
                                                  boolean shouldImport) {
        shouldImport |= originalPosition != null && PsiTreeUtil.isAncestor(containingClass, originalPosition, false);
        return new JavaGlobalMemberLookupElement(overloads, containingClass, QUALIFIED_METHOD_INSERT_HANDLER, STATIC_IMPORT_INSERT_HANDLER,
                                                 shouldImport);
      }

      @Override
      protected boolean isAccessible(PsiMember member) {
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
    return new JavaGlobalMemberLookupElement(member, containingClass, QUALIFIED_METHOD_INSERT_HANDLER, STATIC_IMPORT_INSERT_HANDLER, shouldImport);
  }

  private static final String DUMMY_IDENTIFIER_DECAPITALIZED = StringUtil.decapitalize(CompletionUtil.DUMMY_IDENTIFIER);

  public void beforeCompletion(@NotNull final CompletionInitializationContext context) {
    final String identifier = getIdentifier(context);
    if (identifier != null) {
      context.setDummyIdentifier(identifier);
    }

    //don't eat $ from gstrings when completing previous injection ref. see IDEA-110369
    PsiElement position = context.getFile().findElementAt(context.getStartOffset());
    if (position!= null && position.getNode().getElementType() == mDOLLAR) {
      context.getOffsetMap().addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, context.getStartOffset());
    }
  }

  private static String getIdentifier(CompletionInitializationContext context) {
    if (context.getCompletionType() == CompletionType.BASIC && context.getFile() instanceof GroovyFile) {
      PsiElement position = context.getFile().findElementAt(context.getStartOffset());
      if (position != null &&
          position.getParent() instanceof GrVariable &&
          position == ((GrVariable)position.getParent()).getNameIdentifierGroovy() ||

          position != null &&
          position.getParent() instanceof GrAnnotationNameValuePair &&
          position == ((GrAnnotationNameValuePair)position.getParent()).getNameIdentifierGroovy()) {

        return CompletionUtil.DUMMY_IDENTIFIER_TRIMMED;
      }
      else if (isIdentifierBeforeLParenth(context)) {
        return setCorrectCase(context) + ";";
      }
      else if (isInPossibleClosureParameter(position)) {
        return setCorrectCase(context) + "->";
      }
      else if (isBeforeAssign(context)) {
        return CompletionUtil.DUMMY_IDENTIFIER_TRIMMED;
      }
      else {
        return setCorrectCase(context);
      }
    }
    return null;
  }

  private static boolean isBeforeAssign(CompletionInitializationContext context) {
    //<caret>String name=
    HighlighterIterator iterator = ((EditorEx)context.getEditor()).getHighlighter().createIterator(context.getStartOffset());
    if (iterator.atEnd()) return false;

    if (iterator.getTokenType() == mIDENT) {
      iterator.advance();
    }

    while (!iterator.atEnd() && WHITE_SPACES_OR_COMMENTS.contains(iterator.getTokenType())) {
      iterator.advance();
    }

    return !iterator.atEnd() && iterator.getTokenType() == mASSIGN;
  }

  private static String setCorrectCase(CompletionInitializationContext context) {
    final PsiElement element = context.getFile().findElementAt(context.getStartOffset());
    if (element == null) return DUMMY_IDENTIFIER_DECAPITALIZED;

    final String text = element.getText();
    if (text.length() == 0) return DUMMY_IDENTIFIER_DECAPITALIZED;

    return Character.isUpperCase(text.charAt(0)) ? CompletionInitializationContext.DUMMY_IDENTIFIER : DUMMY_IDENTIFIER_DECAPITALIZED;
  }

  public static boolean isInPossibleClosureParameter(PsiElement position) { //Closure cl={String x, <caret>...
    if (position == null) return false;

    if (position instanceof PsiWhiteSpace || position.getNode().getElementType() == mNLS) {
      position = FilterPositionUtil.searchNonSpaceNonCommentBack(position);
    }

    boolean hasCommas = false;
    while (position != null) {
      PsiElement parent = position.getParent();
      if (parent instanceof GrVariable) {
        PsiElement prev = FilterPositionUtil.searchNonSpaceNonCommentBack(parent);
        hasCommas = prev != null && prev.getNode().getElementType() == mCOMMA;
      }

      if (parent instanceof GrClosableBlock) {
        PsiElement sibling = position.getPrevSibling();
        while (sibling != null) {
          if (sibling instanceof GrParameterList) {
            return hasCommas;
          }

          boolean isComma = sibling instanceof LeafPsiElement && mCOMMA == ((LeafPsiElement)sibling).getElementType();
          hasCommas |= isComma;

          if (isComma ||
              sibling instanceof PsiWhiteSpace ||
              sibling instanceof PsiErrorElement ||
              sibling instanceof GrVariableDeclaration ||
              sibling instanceof GrReferenceExpression && !((GrReferenceExpression)sibling).isQualified()
            ) {
            sibling = sibling.getPrevSibling();
          }
          else {
            return false;
          }
        }
        return false;
      }
      position = parent;
    }
    return false;
  }

  private static boolean isIdentifierBeforeLParenth(CompletionInitializationContext context) { //<caret>String name=
    HighlighterIterator iterator = ((EditorEx)context.getEditor()).getHighlighter().createIterator(context.getStartOffset());
    if (iterator.atEnd()) return false;

    if (iterator.getTokenType() == mIDENT) {
      iterator.advance();
    }

    while (!iterator.atEnd() && WHITE_SPACES_OR_COMMENTS.contains(iterator.getTokenType())) {
      iterator.advance();
    }

    return !iterator.atEnd() && iterator.getTokenType() == mLPAREN;
  }

  public static void suggestVariableNames(PsiElement context, CompletionResultSet result) {
    final PsiElement parent = context.getParent();
    if (GroovyCompletionUtil.isWildcardCompletion(context)) return;
    if (parent instanceof GrVariable) {
      final GrVariable variable = (GrVariable) parent;
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
              result.addElement(LookupElementBuilder.create(newName));
              return;
            }
          }
          for (String name : names) {
            result.addElement(LookupElementBuilder.create(name));
          }
        }

        GrExpression initializer = variable.getInitializerGroovy();
        if (initializer != null) {
          for (String name : GroovyNameSuggestionUtil.suggestVariableNames(initializer, new DefaultGroovyVariableNameValidator(variable),
                                                                        variable.hasModifierProperty(PsiModifier.STATIC))) {
            result.addElement(LookupElementBuilder.create(name));
          }
        }
      }
    }
  }
}
