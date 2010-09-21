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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.completion.handlers.AfterNewClassInsertHandler;
import org.jetbrains.plugins.groovy.lang.completion.handlers.ArrayInsertHandler;
import org.jetbrains.plugins.groovy.lang.completion.handlers.NamedArgumentInsertHandler;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.CompletionProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.util.containers.CollectionFactory.hashMap;
import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.skipWhitespaces;

/**
 * @author ilyas
 */
public class GroovyCompletionContributor extends CompletionContributor {

  private static final ElementPattern<PsiElement> AFTER_NEW =
    psiElement().afterLeaf(psiElement().withText(PsiKeyword.NEW).andNot(psiElement().afterLeaf(psiElement().withText(PsiKeyword.THROW))))
      .withSuperParent(3, GrVariable.class);

  private static final ElementPattern<PsiElement> AFTER_DOT = psiElement().afterLeaf(".").withParent(GrReferenceExpression.class);

  private static final String[] MODIFIERS =
    new String[]{GrModifier.PRIVATE, GrModifier.PUBLIC, GrModifier.PROTECTED, GrModifier.TRANSIENT, GrModifier.ABSTRACT, GrModifier.NATIVE,
      GrModifier.VOLATILE, GrModifier.STRICTFP, GrModifier.DEF, GrModifier.FINAL, GrModifier.SYNCHRONIZED, GrModifier.STATIC};
  private static final ElementPattern<PsiElement> TYPE_IN_VARIABLE_DECLARATION_AFTER_MODIFIER = PlatformPatterns
    .or(psiElement(PsiElement.class).withParent(GrVariable.class).afterLeaf(MODIFIERS),
        psiElement(PsiElement.class).withParent(GrParameter.class));

  private static final ElementPattern<PsiElement> IN_ARGUMENT_LIST_OF_CALL =
    psiElement().withParent(psiElement(GrReferenceExpression.class).withParent(psiElement(GrArgumentList.class).withParent(GrCall.class)));

  private static final String[] THIS_SUPER = {"this", "super"};
  private static final InsertHandler<JavaGlobalMemberLookupElement> STATIC_IMPORT_INSERT_HANDLER = new InsertHandler<JavaGlobalMemberLookupElement>() {
    @Override
    public void handleInsert(InsertionContext context, JavaGlobalMemberLookupElement item) {
      new GroovyInsertHandler().handleInsert(context, item);
      final PsiClass containingClass = item.getContainingClass();
      PsiDocumentManager.getInstance(containingClass.getProject()).commitDocument(context.getDocument());
      final GrReferenceExpression ref = PsiTreeUtil
        .findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), GrReferenceExpression.class, false);
      if (ref != null && ref.getQualifier() == null) {
        ref.bindToElementViaStaticImport(containingClass);
      }

    }
  };
  private static final InsertHandler<JavaGlobalMemberLookupElement> QUALIFIED_METHOD_INSERT_HANDLER = new InsertHandler<JavaGlobalMemberLookupElement>() {
    @Override
    public void handleInsert(InsertionContext context, JavaGlobalMemberLookupElement item) {
      new GroovyInsertHandler().handleInsert(context, item);
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

  public static boolean isReferenceInNewExpression(PsiElement reference) {
    if (!(reference instanceof GrCodeReferenceElement)) return false;

    PsiElement parent = reference.getParent();
    while (parent instanceof GrCodeReferenceElement) parent = parent.getParent();
    if (parent instanceof GrAnonymousClassDefinition) parent = parent.getParent();
    return parent instanceof GrNewExpression;
  }

  public GroovyCompletionContributor() {
    extend(CompletionType.BASIC, psiElement(PsiElement.class), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull final CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();
        final PsiElement reference = position.getParent();
        if (reference instanceof GrReferenceElement) {
          final Map<PsiModifierListOwner, LookupElement> staticMembers = hashMap();

          ((GrReferenceElement)reference).processVariants(new Consumer<Object>() {
            public void consume(Object element) {
              final LookupElement lookupElement = element instanceof PsiClass
                                                  ? AllClassesGetter.createLookupItem((PsiClass)element)
                                                  : GroovyCompletionUtil.getLookupElement(element);
              final Object object = lookupElement.getObject();
              if ((object instanceof PsiMethod || object instanceof PsiField) && ((PsiModifierListOwner)object).hasModifierProperty(PsiModifier.STATIC)) {
                if (lookupElement.getLookupString().equals(((PsiMember)object).getName())) {
                  staticMembers.put((PsiModifierListOwner)object, lookupElement);
                  return;
                }
              }
              result.addElement(lookupElement);
            }
          });

          if (((GrReferenceElement)reference).getQualifier() == null) {
            completeStaticMembers(position).processMembersOfRegisteredClasses(null, new PairConsumer<PsiMember, PsiClass>() {
              @Override
              public void consume(PsiMember member, PsiClass psiClass) {
                if (member instanceof GrAccessorMethod) {
                  member = ((GrAccessorMethod)member).getProperty();
                }
                final String name = member.getName();
                if (name == null || !result.getPrefixMatcher().prefixMatches(name)) {
                  staticMembers.remove(member);
                  return;
                }
                staticMembers.put(member, new JavaGlobalMemberLookupElement(member, psiClass, QUALIFIED_METHOD_INSERT_HANDLER, STATIC_IMPORT_INSERT_HANDLER, true));
              }
            });
          }
          result.addAllElements(staticMembers.values());
        }
      }
    });

    extend(CompletionType.SMART, AFTER_NEW, new CompletionProvider<CompletionParameters>(false) {
      public void addCompletions(@NotNull final CompletionParameters parameters,
                                 final ProcessingContext matchingContext,
                                 @NotNull final CompletionResultSet result) {
        final PsiElement identifierCopy = parameters.getPosition();
        final PsiFile file = parameters.getOriginalFile();

        final List<PsiClassType> expectedClassTypes = new SmartList<PsiClassType>();
        final List<PsiArrayType> expectedArrayTypes = new ArrayList<PsiArrayType>();

        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            PsiType psiType = ((GrVariable)identifierCopy.getParent().getParent().getParent()).getTypeGroovy();
            if (psiType instanceof PsiClassType) {
              PsiType type = JavaCompletionUtil.eliminateWildcards(JavaCompletionUtil.originalize(psiType));
              final PsiClassType classType = (PsiClassType)type;
              if (classType.resolve() != null) {
                expectedClassTypes.add(classType);
              }
            }
            else if (psiType instanceof PsiArrayType) {
              expectedArrayTypes.add((PsiArrayType)psiType);
            }
          }
        });

        for (final PsiArrayType type : expectedArrayTypes) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              final LookupItem item = PsiTypeLookupItem.createLookupItem(JavaCompletionUtil.eliminateWildcards(type), identifierCopy);
              if (item.getObject() instanceof PsiClass) {
                JavaCompletionUtil.setShowFQN(item);
              }
              item.setInsertHandler(new ArrayInsertHandler());
              result.addElement(item);
            }
          });
        }

        JavaSmartCompletionContributor.processInheritors(parameters, identifierCopy, file, expectedClassTypes, new Consumer<PsiType>() {
          public void consume(final PsiType type) {
            addExpectedType(result, type, identifierCopy);
          }
        }, result.getPrefixMatcher());
      }
    });

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
        if (!org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.hasEnclosingInstanceInScope((PsiClass)resolved, position, false)) return;

        for (String keyword : THIS_SUPER) {
          result.addElement(LookupElementBuilder.create(keyword));
        }
      }
    });

    extend(CompletionType.BASIC, TYPE_IN_VARIABLE_DECLARATION_AFTER_MODIFIER, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();
        if (!GroovyCompletionUtil.isFirstElementAfterModifiersInVariableDeclaration(position, true)) return;

        ResolverProcessor processor = CompletionProcessor.createClassCompletionProcessor(position);
        ResolveUtil.treeWalkUp((GrVariable)position.getParent(), processor, false);

        for (Object variant : GroovyCompletionUtil.getCompletionVariants(processor.getCandidates())) {
          final String lookupString;
          if (variant instanceof PsiElement) {
            lookupString = PsiUtilBase.getName(((PsiElement)variant));
          }
          else {
            lookupString = variant.toString();
          }
          if (lookupString == null) continue;

          LookupElementBuilder builder = LookupElementBuilder.create(variant, lookupString);
          if (variant instanceof Iconable) {
            builder = builder.setIcon(((Iconable)variant).getIcon(Iconable.ICON_FLAG_VISIBILITY));
          }

          if (variant instanceof PsiClass) {
            String packageName = PsiFormatUtil.getPackageDisplayName((PsiClass)variant);
            builder = builder.setTailText(" (" + packageName + ")", true);
          }
          builder.setInsertHandler(new GroovyInsertHandler());
          result.addElement(builder);
        }
      }
    });

    extend(CompletionType.BASIC, IN_ARGUMENT_LIST_OF_CALL, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        final GrReferenceExpression refExpr = ((GrReferenceExpression)parameters.getPosition().getParent());
        if (refExpr.getQualifier() != null) return;
        final GrArgumentList argumentList = (GrArgumentList)refExpr.getParent();
        final GrCall call = (GrCall)argumentList.getParent();
        List<GroovyResolveResult> results = new ArrayList<GroovyResolveResult>();
        //costructor call
        if (call instanceof GrConstructorCall) {
          GrConstructorCall constructorCall = (GrConstructorCall)call;
          ContainerUtil.addAll(results, constructorCall.multiResolveConstructor());
          ContainerUtil.addAll(results, constructorCall.multiResolveClass());
        }
        else if (call instanceof GrCallExpression) {
          GrCallExpression constructorCall = (GrCallExpression)call;
          ContainerUtil.addAll(results, constructorCall.getCallVariants(null));
          final PsiType type = ((GrCallExpression)call).getType();
          if (type instanceof PsiClassType) {
            final PsiClass psiClass = ((PsiClassType)type).resolve();
            results.add(new GroovyResolveResultImpl(psiClass, true));
          }
        }
        else if (call instanceof GrApplicationStatement) {
          final GrExpression element = ((GrApplicationStatement)call).getInvokedExpression();
          if (element instanceof GrReferenceElement) {
            ContainerUtil.addAll(results, ((GrReferenceElement)element).multiResolve(true));
          }
        }

        Set<PsiClass> usedClasses = new HashSet<PsiClass>();
        Set<String> usedNames = new HashSet<String>();
        for (GrNamedArgument argument : argumentList.getNamedArguments()) {
          final GrArgumentLabel label = argument.getLabel();
          if (label != null) {
            final String name = label.getName();
            if (name != null) {
              usedNames.add(name);
            }
          }
        }

        for (GroovyResolveResult resolveResult : results) {
          PsiElement element = resolveResult.getElement();
          if (element instanceof PsiMethod) {
            final PsiMethod method = (PsiMethod)element;
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
              addPropertiesForClass(result, usedClasses, usedNames, containingClass, call);
            }
            if (method instanceof GrMethod) {
              for (String parameter : ((GrMethod)method).getNamedParametersArray()) {
                final LookupElementBuilder lookup =
                  LookupElementBuilder.create(parameter).setIcon(GroovyIcons.DYNAMIC).setInsertHandler(NamedArgumentInsertHandler.INSTANCE);
                result.addElement(lookup);
              }
            }
          }
          else if (element instanceof PsiClass) {
            addPropertiesForClass(result, usedClasses, usedNames, (PsiClass)element, call);
          }
        }
      }
    });

    extend(CompletionType.BASIC, psiElement().withParent(GrReferenceElement.class), new CompletionProvider<CompletionParameters>(false) {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull final CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();
        if (((GrReferenceElement)position.getParent()).getQualifier() != null) return;

        final String s = result.getPrefixMatcher().getPrefix();
        if (StringUtil.isEmpty(s) || !Character.isUpperCase(s.charAt(0))) return;

        result.runRemainingContributors(new CompletionParameters(position, parameters.getOriginalFile(), CompletionType.CLASS_NAME, parameters.getOffset(), parameters.getInvocationCount()), new Consumer<LookupElement>() {
          @Override
          public void consume(LookupElement lookupElement) {
            result.addElement(lookupElement);
          }
        });

      }
    });
    extend(CompletionType.CLASS_NAME, psiElement().withParent(GrReferenceElement.class), new CompletionProvider<CompletionParameters>(false) {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull final CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();
        if (((GrReferenceElement)position.getParent()).getQualifier() != null) return;

        final String s = result.getPrefixMatcher().getPrefix();
        if (StringUtil.isEmpty(s) || !Character.isLowerCase(s.charAt(0))) return;

        completeStaticMembers(position).processStaticMethodsGlobally(result);
      }
    });
  }

  private static StaticMemberProcessor completeStaticMembers(PsiElement position) {
    final StaticMemberProcessor processor = new StaticMemberProcessor(position) {
      @NotNull
      @Override
      protected LookupElement createLookupElement(@NotNull PsiMember member, @NotNull PsiClass containingClass, boolean shouldImport) {
        return new JavaGlobalMemberLookupElement(member, containingClass, QUALIFIED_METHOD_INSERT_HANDLER, STATIC_IMPORT_INSERT_HANDLER,
                                                 shouldImport);
      }
    };
    final PsiFile file = position.getContainingFile();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
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
      }
    });
    return processor;
  }

  private static void addPropertiesForClass(CompletionResultSet result,
                                            Set<PsiClass> usedClasses,
                                            Set<String> usedNames,
                                            PsiClass containingClass,
                                            GrCall call) {
    if (usedClasses.contains(containingClass)) return;
    usedClasses.add(containingClass);
    final PsiClass eventListener =
      JavaPsiFacade.getInstance(call.getProject()).findClass("java.util.EventListener", call.getResolveScope());
    Map<String, PsiMethod> writableProperties = new HashMap<String, PsiMethod>();
    for (PsiMethod method : containingClass.getAllMethods()) {
      if (GroovyPropertyUtils.isSimplePropertySetter(method)) {
        if (org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isStaticsOK(method, call)) {
          final String name = GroovyPropertyUtils.getPropertyNameBySetter(method);
          if (name != null && !writableProperties.containsKey(name)) {
            writableProperties.put(name, method);
          }
        }
      }
      else if (eventListener != null) {
        consumeListenerProperties(result, usedNames, method, eventListener);
      }
    }

    for (String name : writableProperties.keySet()) {
      if (usedNames.contains(name)) continue;
      usedNames.add(name);
      final LookupElementBuilder builder =
        LookupElementBuilder.create(writableProperties.get(name), name).setIcon(GroovyIcons.PROPERTY)
          .setInsertHandler(NamedArgumentInsertHandler.INSTANCE);
      result.addElement(builder);
    }
  }

  private static void consumeListenerProperties(CompletionResultSet result,
                                                Set<String> usedNames, PsiMethod method, PsiClass eventListenerClass) {
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
              final String name = listenerMethod.getName();
              usedNames.add(name);
              result.addElement(LookupElementBuilder.create(name).setIcon(GroovyIcons.PROPERTY).setInsertHandler(NamedArgumentInsertHandler.INSTANCE));
            }
          }
        }
      }
    }
  }


  private static boolean checkForInnerClass(PsiClass psiClass, PsiElement identifierCopy) {
    return !PsiUtil.isInnerClass(psiClass) ||
           org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
             .hasEnclosingInstanceInScope(psiClass.getContainingClass(), identifierCopy, true);
  }

  private static void addExpectedType(final CompletionResultSet result, final PsiType type, final PsiElement place) {
    if (!JavaCompletionUtil.hasAccessibleConstructor(type)) return;

    final PsiClass psiClass = PsiUtil.resolveClassInType(type);
    if (psiClass == null) return;

    if (psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) return;
    if (!checkForInnerClass(psiClass, place)) return;

    final LookupItem item = PsiTypeLookupItem.createLookupItem(JavaCompletionUtil.eliminateWildcards(type), place);
    JavaCompletionUtil.setShowFQN(item);
    item.setInsertHandler(new AfterNewClassInsertHandler((PsiClassType)type, place));
    result.addElement(item);
  }

  public void beforeCompletion(@NotNull final CompletionInitializationContext context) {
    final PsiFile file = context.getFile();
    final Project project = context.getProject();
    JavaCompletionUtil.initOffsets(file, project, context.getOffsetMap());
    if (context.getCompletionType() == CompletionType.BASIC && file instanceof GroovyFile) {
      if (semicolonNeeded(context)) {
        context.setFileCopyPatcher(new DummyIdentifierPatcher(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED + ";"));
      }
      else if (isInClosurePropertyParameters(context)) {
        context.setFileCopyPatcher(new DummyIdentifierPatcher(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED + "->"));
      }
    }
  }

  private static boolean isInClosurePropertyParameters(CompletionInitializationContext context) { //Closure cl={String x, <caret>...
    final PsiFile file = context.getFile();                                                       //Closure cl={String x, String <caret>...
    final PsiElement position = file.findElementAt(context.getStartOffset());
    if (position == null) return false;

    GrVariableDeclaration declaration = PsiTreeUtil.getParentOfType(position, GrVariableDeclaration.class, false, GrStatement.class);
    if (declaration == null) {
      PsiElement prev = position.getPrevSibling();
      prev = skipWhitespaces(prev, false);
      if (prev instanceof PsiErrorElement) {
        prev = prev.getPrevSibling();
      }
      prev = skipWhitespaces(prev, false);
      if (prev instanceof GrVariableDeclaration) declaration = (GrVariableDeclaration)prev;
    }
    if (declaration != null) {
      if (!(declaration.getParent() instanceof GrClosableBlock)) return false;
      PsiElement prevSibling = skipWhitespaces(declaration.getPrevSibling(), false);
      return prevSibling instanceof GrParameterList;
    }
    return false;
  }

  private static boolean semicolonNeeded(CompletionInitializationContext context) { //<caret>String name=
    HighlighterIterator iterator = ((EditorEx)context.getEditor()).getHighlighter().createIterator(context.getStartOffset());
    if (iterator.atEnd()) return false;

    if (iterator.getTokenType() == GroovyTokenTypes.mIDENT) {
      iterator.advance();
    }

    if (!iterator.atEnd() && iterator.getTokenType() == GroovyTokenTypes.mLPAREN) {
      return true;
    }

    while (!iterator.atEnd() && GroovyTokenTypes.WHITE_SPACES_OR_COMMENTS.contains(iterator.getTokenType())) {
      iterator.advance();
    }

    if (iterator.atEnd() || iterator.getTokenType() != GroovyTokenTypes.mIDENT) return false;
    iterator.advance();

    while (!iterator.atEnd() && GroovyTokenTypes.WHITE_SPACES_OR_COMMENTS.contains(iterator.getTokenType())) {
      iterator.advance();
    }
    return true;
  }

}
