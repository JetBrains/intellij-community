/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.completion.handlers.AfterNewClassInsertHandler;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.template.expressions.ChooseTypeExpression;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GroovySmartCompletionContributor extends CompletionContributor {
  private static final ElementPattern<PsiElement> INSIDE_EXPRESSION = PlatformPatterns.psiElement().withParent(GrExpression.class);
  private static final ElementPattern<PsiElement> IN_CAST_PARENTHESES =
    PlatformPatterns.psiElement().withSuperParent(3, PlatformPatterns.psiElement(GrTypeCastExpression.class).withParent(
      StandardPatterns.or(PlatformPatterns.psiElement(GrAssignmentExpression.class), PlatformPatterns.psiElement(GrVariable.class))));

  static final ElementPattern<PsiElement> AFTER_NEW = PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(
    GroovyTokenTypes.kNEW));

  private static final ElementPattern<PsiElement> IN_ANNOTATION = PlatformPatterns
    .psiElement().withParent(PlatformPatterns.psiElement(GrReferenceExpression.class).withParent(GrAnnotationNameValuePair.class));

  private static final TObjectHashingStrategy<TypeConstraint> EXPECTED_TYPE_INFO_STRATEGY =
    new TObjectHashingStrategy<TypeConstraint>() {
      @Override
      public int computeHashCode(final TypeConstraint object) {
        return object.getType().hashCode();
      }

      @Override
      public boolean equals(final TypeConstraint o1, final TypeConstraint o2) {
        return o1.getClass().equals(o2.getClass()) && o1.getType().equals(o2.getType());
      }
    };

  public GroovySmartCompletionContributor() {
    extend(CompletionType.SMART, INSIDE_EXPRESSION, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull final CompletionParameters params,
                                    ProcessingContext context,
                                    @NotNull final CompletionResultSet result) {
        final PsiElement position = params.getPosition();
        if (position.getParent() instanceof GrLiteral) return;

        if (isInDefaultAnnotationNameValuePair(position)) return;

        final Set<TypeConstraint> infos = getExpectedTypeInfos(params);

        final PsiElement reference = position.getParent();
        if (reference == null) return;
        if (reference instanceof GrReferenceElement) {
          GroovyCompletionUtil.processVariants((GrReferenceElement)reference, result.getPrefixMatcher(), params,
                                               variant -> {
                                                 PsiType type = null;

                                                 Object o = variant.getObject();
                                                 if (o instanceof GroovyResolveResult) {
                                                   if (!((GroovyResolveResult)o).isAccessible()) return;
                                                   o = ((GroovyResolveResult)o).getElement();
                                                 }

                                                 if (o instanceof PsiElement) {
                                                   type = getTypeByElement((PsiElement)o, position);
                                                 }
                                                 else if (o instanceof String) {
                                                   if ("true".equals(o) || "false".equals(o)) {
                                                     type = PsiType.BOOLEAN;
                                                   }
                                                 }
                                                 if (type == null) return;
                                                 for (TypeConstraint info : infos) {
                                                   if (info.satisfied(type, position)) {
                                                     result.addElement(variant);
                                                     break;
                                                   }
                                                 }
                                               });
        }

        addExpectedClassMembers(params, result);
      }
    });

    extend(CompletionType.SMART, IN_CAST_PARENTHESES, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();
        final GrTypeCastExpression parenthesizedExpression = ((GrTypeCastExpression)position.getParent().getParent().getParent());
        final PsiElement assignment = parenthesizedExpression.getParent();
        if (assignment instanceof GrAssignmentExpression &&
            ((GrAssignmentExpression)assignment).getLValue() == parenthesizedExpression) {
          return;
        }

        final boolean overwrite = PlatformPatterns.psiElement()
          .afterLeaf(PlatformPatterns.psiElement().withText("(").withParent(GrTypeCastExpression.class))
          .accepts(parameters.getOriginalPosition());
        final Set<TypeConstraint> typeConstraints = getExpectedTypeInfos(parameters);
        for (TypeConstraint typeConstraint : typeConstraints) {
          final PsiType type = typeConstraint.getType();
          final PsiTypeLookupItem item = PsiTypeLookupItem.createLookupItem(type, position, PsiTypeLookupItem.isDiamond(type), ChooseTypeExpression.IMPORT_FIXER).setShowPackage();
          item.setInsertHandler(new InsertHandler<LookupElement>() {
            @Override
            public void handleInsert(InsertionContext context, LookupElement item) {
              FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.smarttype.casting");

              final Editor editor = context.getEditor();
              final Document document = editor.getDocument();
              if (overwrite) {
                document.deleteString(context.getSelectionEndOffset(),
                                      context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET));
              }

              final CommonCodeStyleSettings csSettings =
                CodeStyleSettingsManager.getSettings(context.getProject()).getCommonSettings(GroovyLanguage.INSTANCE);
              final int oldTail = context.getTailOffset();
              context.setTailOffset(GroovyCompletionUtil.addRParenth(editor, oldTail, csSettings.SPACE_WITHIN_CAST_PARENTHESES));

              if (csSettings.SPACE_AFTER_TYPE_CAST) {
                context.setTailOffset(TailType.insertChar(editor, context.getTailOffset(), ' '));
              }

              editor.getCaretModel().moveToOffset(context.getTailOffset());
              editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
              GroovyCompletionUtil.addImportForItem(context.getFile(), context.getStartOffset(), item);
            }
          });
          result.addElement(item);
        }
      }
    });

    extend(CompletionType.SMART, AFTER_NEW, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull final CompletionParameters parameters,
                                 final ProcessingContext matchingContext,
                                 @NotNull final CompletionResultSet result) {
        generateInheritorVariants(parameters, result.getPrefixMatcher(), lookupElement -> result.addElement(lookupElement));
      }
    });

    extend(CompletionType.SMART, IN_ANNOTATION, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters params,
                                    ProcessingContext context,
                                    @NotNull final CompletionResultSet result) {
        final PsiElement position = params.getPosition();

        if (!isInDefaultAnnotationNameValuePair(position)) return;

        final GrReferenceExpression reference = (GrReferenceExpression)position.getParent();
        if (reference == null) return;

        CompleteReferenceExpression.processRefInAnnotation(reference, result.getPrefixMatcher(), element -> {
          if (element != null) {
            result.addElement(element);
          }
        }, params);
      }
    });

  }

  /**
   * we are here: @Abc(<caret>)
   * where Abc does not have 'value' attribute
   */
  private static boolean isInDefaultAnnotationNameValuePair(PsiElement position) {
    PsiElement parent = position.getParent();
    if (parent instanceof GrReferenceExpression) {
      PsiElement pparent = parent.getParent();
      if (pparent instanceof GrAnnotationNameValuePair) {
        PsiElement identifier = ((GrAnnotationNameValuePair)pparent).getNameIdentifierGroovy();
        if (identifier == null) {
          PsiElement ppparent = pparent.getParent().getParent();
          if (ppparent instanceof GrAnnotation) {
            PsiElement resolved = ((GrAnnotation)ppparent).getClassReference().resolve();
            if (resolved instanceof PsiClass && ((PsiClass)resolved).isAnnotationType()) {
              PsiMethod[] values = ((PsiClass)resolved).findMethodsByName("value", false);
              return values.length == 0;
            }
          }
        }
      }
    }

    return false;
  }

  static void addExpectedClassMembers(CompletionParameters params, final CompletionResultSet result) {
    for (final TypeConstraint info : getExpectedTypeInfos(params)) {
      Consumer<LookupElement> consumer = element -> result.addElement(element);
      PsiType type = info.getType();
      PsiType defType = info.getDefaultType();
      boolean searchInheritors = params.getInvocationCount() > 1;
      if (type instanceof PsiClassType) {
        new GroovyMembersGetter((PsiClassType)type, params).processMembers(searchInheritors, consumer);
      }
      if (!defType.equals(type) && defType instanceof PsiClassType) {
        new GroovyMembersGetter((PsiClassType)defType, params).processMembers(searchInheritors, consumer);
      }
    }
  }

  static void generateInheritorVariants(final CompletionParameters parameters, PrefixMatcher matcher, final Consumer<LookupElement> consumer) {
    final PsiElement place = parameters.getPosition();
    final GrExpression expression = PsiTreeUtil.getParentOfType(place, GrExpression.class);
    if (expression == null) return;

    GrExpression placeToInferType = expression;
    if (expression.getParent() instanceof GrApplicationStatement && expression.getParent().getParent() instanceof GrAssignmentExpression) {
      placeToInferType = (GrExpression)expression.getParent();
    }

    for (PsiType type : GroovyExpectedTypesProvider.getDefaultExpectedTypes(placeToInferType)) {
      if (type instanceof PsiArrayType) {
        final PsiType _type = GenericsUtil.eliminateWildcards(type);
        final PsiTypeLookupItem item = PsiTypeLookupItem.createLookupItem(_type, place, PsiTypeLookupItem.isDiamond(_type), ChooseTypeExpression.IMPORT_FIXER).setShowPackage();
        if (item.getObject() instanceof PsiClass) {
          item.setInsertHandler(new InsertHandler<LookupElement>() {
            @Override
            public void handleInsert(InsertionContext context, LookupElement item) {
              GroovyCompletionUtil.addImportForItem(context.getFile(), context.getStartOffset(), item);
            }
          });
        }
        consumer.consume(item);
      }
    }


    final List<PsiClassType> expectedClassTypes = new SmartList<>();

    for (PsiType psiType : GroovyExpectedTypesProvider.getDefaultExpectedTypes(placeToInferType)) {
      if (psiType instanceof PsiClassType) {
        PsiType type = GenericsUtil.eliminateWildcards(JavaCompletionUtil.originalize(psiType));
        final PsiClassType classType = (PsiClassType)type;
        if (classType.resolve() != null) {
          expectedClassTypes.add(classType);
        }
      }
    }

    final PsiType diamond = inferDiamond(place);

    JavaInheritorsGetter.processInheritors(parameters, expectedClassTypes, matcher, type -> {
      final LookupElement element = addExpectedType(type, place, parameters, diamond);
      if (element != null) {
        consumer.consume(element);
      }
    });
  }

  @Nullable
  private static PsiType inferDiamond(PsiElement place) {
    if (!GroovyConfigUtils.getInstance().isVersionAtLeast(place, GroovyConfigUtils.GROOVY1_8)) {
      return null;
    }

    final PsiElement parent = place.getParent().getParent();
    if (!(parent instanceof GrNewExpression)) return null;

    final PsiElement pparent = parent.getParent();

    if (pparent instanceof GrVariable) {
      return ((GrVariable)pparent).getDeclaredType();
    }
    else if (pparent instanceof GrAssignmentExpression) {
      GrAssignmentExpression assignment = (GrAssignmentExpression)pparent;

      GrExpression lvalue = assignment.getLValue();
      GrExpression rvalue = assignment.getRValue();

      if (parent == rvalue && !assignment.isOperatorAssignment()) {
        return lvalue.getNominalType();
      }
    }
    else if (pparent instanceof GrApplicationStatement) {
      PsiElement ppparent = pparent.getParent();
      if (ppparent instanceof GrAssignmentExpression) {
        GrAssignmentExpression assignment = (GrAssignmentExpression)ppparent;

        GrExpression lvalue = assignment.getLValue();
        GrExpression rvalue = assignment.getRValue();

        if (pparent == rvalue && !assignment.isOperatorAssignment()) {
          return lvalue.getNominalType();
        }
      }
    }
    return null;
  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    super.fillCompletionVariants(parameters, result);
  }

  @Nullable
  private static LookupElement addExpectedType(PsiType type, final PsiElement place, CompletionParameters parameters, @Nullable PsiType diamond) {
    if (!JavaCompletionUtil.hasAccessibleConstructor(type)) return null;

    final PsiClass psiClass = com.intellij.psi.util.PsiUtil.resolveClassInType(type);
    if (psiClass == null) return null;

    if (!checkForInnerClass(psiClass, place)) return null;


    boolean isDiamond = false;
    if (diamond != null &&
        psiClass.hasTypeParameters() &&
        !((PsiClassType)type).isRaw() &&
        !psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      final String canonicalText = TypeConversionUtil.erasure(type).getCanonicalText();
      final GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(place.getProject());
      final String text = diamond.getCanonicalText() + " v = new " + canonicalText + "<>()";
      final GrStatement statement = elementFactory.createStatementFromText(text, parameters.getOriginalFile());
      final GrVariable declaredVar = ((GrVariableDeclaration)statement).getVariables()[0];
      final GrNewExpression initializer = (GrNewExpression)declaredVar.getInitializerGroovy();
      assert initializer != null;
      final boolean hasDefaultConstructorOrNoGenericsOne = PsiDiamondTypeImpl.hasDefaultConstructor(psiClass) ||
                                                           !PsiDiamondTypeImpl.haveConstructorsGenericsParameters(psiClass);
      final PsiType initializerType = initializer.getType();
      if (hasDefaultConstructorOrNoGenericsOne &&
          initializerType instanceof PsiClassType &&
          ((PsiClassType)initializerType).getParameters().length > 0) {
        type = initializerType;
        isDiamond = true;
      }
    }

    final PsiTypeLookupItem item = PsiTypeLookupItem.createLookupItem(GenericsUtil.eliminateWildcards(type), place, isDiamond, ChooseTypeExpression.IMPORT_FIXER).setShowPackage();
    Object object = item.getObject();
    if (object instanceof PsiClass && ((PsiClass)object).hasModifierProperty(PsiModifier.ABSTRACT)) {
      item.setIndicateAnonymous(true);
    }
    item.setInsertHandler(new AfterNewClassInsertHandler((PsiClassType)type, true));
    return item;
  }

  private static boolean checkForInnerClass(PsiClass psiClass, PsiElement identifierCopy) {
    return !com.intellij.psi.util.PsiUtil.isInnerClass(psiClass) ||
           PsiUtil.hasEnclosingInstanceInScope(psiClass.getContainingClass(), identifierCopy, true);
  }


  @Override
  public void beforeCompletion(@NotNull CompletionInitializationContext context) {
    if (context.getCompletionType() != CompletionType.SMART) return;

    PsiElement lastElement = context.getFile().findElementAt(context.getStartOffset() - 1);
    if (lastElement != null && lastElement.getText().equals("(")) {
      final PsiElement parent = lastElement.getParent();
      if (parent instanceof GrTypeCastExpression) {
        context.setDummyIdentifier("");
      }
      else if (parent instanceof GrParenthesizedExpression) {
        context.setDummyIdentifier("xxx)yyy "); // to handle type cast
      }
    }
  }

  private static Set<TypeConstraint> getExpectedTypeInfos(final CompletionParameters params) {
    return new THashSet<>(Arrays.asList(getExpectedTypes(params)), EXPECTED_TYPE_INFO_STRATEGY);
  }

  @NotNull
  public static TypeConstraint[] getExpectedTypes(CompletionParameters params) {
    final PsiElement position = params.getPosition();
    final GrExpression expression = PsiTreeUtil.getParentOfType(position, GrExpression.class);
    if (expression != null) {
      return GroovyExpectedTypesProvider.calculateTypeConstraints(expression);
    }
    return TypeConstraint.EMPTY_ARRAY;
  }

  @Nullable
  public static PsiType getTypeByElement(PsiElement element, PsiElement context) {
    //if(!element.isValid()) return null;
    if (element instanceof PsiType) {
      return (PsiType)element;
    }
    if (element instanceof PsiClass) {
      return PsiType.getJavaLangClass(context.getManager(), GlobalSearchScope.allScope(context.getProject()));
    }
    if (element instanceof PsiMethod) {
      return PsiUtil.getSmartReturnType((PsiMethod)element);
    }
    if (element instanceof GrVariable) {
        return TypeInferenceHelper.getVariableTypeInContext(context, (GrVariable)element);
    }

    if (element instanceof GrExpression) {
      return ((GrExpression)element).getType();
    }
    if (element instanceof PsiField) {
      return ((PsiField)element).getType();
    }

    return null;
  }
}
