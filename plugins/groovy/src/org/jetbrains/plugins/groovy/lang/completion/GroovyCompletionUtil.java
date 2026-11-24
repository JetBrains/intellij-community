// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.AllClassesGetter;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.JavaClassNameCompletionContributor;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrCodeReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.resolve.ReferencesKt.resolvePackageFqn;
import static org.jetbrains.plugins.groovy.lang.resolve.impl.ConstructorsKt.getAllConstructors;

public final class GroovyCompletionUtil {

  private static volatile boolean isSlowCompletionEnabled = true;
  private static final TokenSet SEPARATORS = TokenSet.create(GroovyTokenTypes.mNLS, GroovyTokenTypes.mSEMI);

  private GroovyCompletionUtil() {}

  /**
   * Return true if last element of current statement is expression
   */
  public static boolean endsWithExpression(PsiElement statement) {
    while (statement != null && !(statement instanceof GrExpression)) {
      statement = statement.getLastChild();
      if (statement instanceof PsiErrorElement) {
        statement = nearestLeftSibling(statement);
      }
    }
    return statement != null;
  }

  @TestOnly
  public static void disableSlowCompletionElements(Disposable disposable) {
    boolean previousValue = isSlowCompletionEnabled;
    isSlowCompletionEnabled = false;
    Disposer.register(disposable, () -> isSlowCompletionEnabled = previousValue);
  }

  static boolean isSlowCompletionEnabled() {
    return isSlowCompletionEnabled;
  }

  public static @Nullable PsiElement nearestLeftSibling(PsiElement elem) {
    do {
      elem = elem.getPrevSibling();
    }
    while (elem != null &&
           (elem instanceof PsiWhiteSpace ||
            elem instanceof PsiComment ||
            GroovyTokenTypes.mNLS.equals(elem.getNode().getElementType())));
    return elem;
  }

  public static @Nullable PsiElement nearestLeftLeaf(PsiElement elem) {
    do {
      elem = PsiTreeUtil.prevLeaf(elem);
    }
    while (elem != null &&
           (elem instanceof PsiWhiteSpace ||
            elem instanceof PsiComment ||
            GroovyTokenTypes.mNLS.equals(elem.getNode().getElementType())));
    return elem;
  }

  /**
   * Shows whether keyword may be placed as a new statement beginning
   *
   * @param canBeAfterBrace May be after '{' symbol or not
   */
  public static boolean isNewStatement(PsiElement element, boolean canBeAfterBrace) {
    PsiElement previousLeaf = getLeafByOffset(element.getTextRange().getStartOffset() - 1, element);
    previousLeaf = PsiImplUtil.realPrevious(previousLeaf);
    if (previousLeaf != null) {
      if (canBeAfterBrace && GroovyTokenTypes.mLCURLY.equals(previousLeaf.getNode().getElementType())) {
        return true;
      }
      if (GroovyTokenTypes.mCOLON.equals(previousLeaf.getNode().getElementType()) && previousLeaf.getParent() instanceof GrLabeledStatement) {
        return true;
      }
    }
    return (previousLeaf == null || SEPARATORS.contains(previousLeaf.getNode().getElementType()));
  }

  public static @Nullable PsiElement getLeafByOffset(int offset, PsiElement element) {
    if (offset < 0) {
      return null;
    }
    PsiElement candidate = element.getContainingFile();
    while (candidate.getNode().getFirstChildNode() != null) {
      candidate = candidate.findElementAt(offset);
    }
    return candidate;
  }

  /**
   * return true, if the element is first element after modifiers and there is no type element
   */
  public static boolean isFirstElementAfterPossibleModifiersInVariableDeclaration(PsiElement element, boolean acceptParameter) {
    if (element.getParent() instanceof GrTypeDefinitionBody && !(element instanceof PsiComment)) {
      //is first on the line?
      String text = element.getContainingFile().getText();
      int i = CharArrayUtil.shiftBackward(text, element.getTextRange().getStartOffset() - 1, " \t");
      return i >= 0 && (text.charAt(i) == '\n' || text.charAt(i) == '{');
    }

    final PsiElement parent = element.getParent();
    if (!(parent instanceof GrVariable)) return false;

    if (acceptParameter && parent instanceof GrParameter parameter) {
      return parameter.getTypeElementGroovy() == null;
    }

    final PsiElement pparent = parent.getParent();
    if (!(pparent instanceof GrVariableDeclaration variableDeclaration)
        || variableDeclaration.isTuple()
        || variableDeclaration.getTypeElementGroovy() != null) {
      return false;
    }

    return variableDeclaration.getVariables()[0] == parent;
  }

  public static boolean asSimpleVariable(PsiElement context) {
    return isInTypeDefinitionBody(context) && isNewStatement(context, true);
  }

  public static boolean isInTypeDefinitionBody(PsiElement context) {
    PsiElement parent = context.getParent();
    return (parent instanceof GrCodeReferenceElement ref
            && ref.getParent() instanceof GrClassTypeElement element
            && element.getParent() instanceof GrTypeDefinitionBody)
           || parent instanceof GrTypeDefinitionBody;
  }

  public static boolean asVariableInBlock(PsiElement context) {
    if (context.getParent() instanceof GrReferenceExpression) {
      PsiElement parent = context.getParent().getParent();
      while (parent instanceof GrStatement) {
        parent = parent.getParent();
      }
      if ((parent instanceof GrControlFlowOwner || parent instanceof GrCaseSection) && isNewStatement(context, true)) {
        return true;
      }
    }

    return context.getParent() instanceof GrTypeDefinitionBody && isNewStatement(context, true);
  }

  public static boolean asTypedMethod(PsiElement context) {
    return context.getParent() instanceof GrReferenceElement<?> ref &&
           ref.getParent() instanceof GrTypeElement element &&
           element.getParent() instanceof GrMethod method &&
           method.getParent() instanceof GrTypeDefinitionBody body &&
           context.getTextRange().getStartOffset() == body.getTextRange().getStartOffset();
  }


  public static List<LookupElement> getCompletionVariants(GroovyResolveResult[] candidates,
                                                          boolean afterNew,
                                                          PrefixMatcher matcher,
                                                          PsiElement position) {
    List<LookupElement> result = new ArrayList<>();
    for (GroovyResolveResult candidate : candidates) {
      result.addAll(createLookupElements(candidate, afterNew, matcher, position));
      ProgressManager.checkCanceled();
    }

    return result;
  }

  public static List<LookupElement> getCompletionVariants(List<? extends GroovyResolveResult> candidates,
                                                          boolean afterNew,
                                                          PrefixMatcher matcher,
                                                          PsiElement position) {
    List<LookupElement> result = new ArrayList<>();
    for (GroovyResolveResult candidate : candidates) {
      result.addAll(createLookupElements(candidate, afterNew, matcher, position));
      ProgressManager.checkCanceled();
    }

    return result;
  }


  public static List<? extends LookupElement> createLookupElements(@NotNull GroovyResolveResult candidate,
                                                                   boolean afterNew,
                                                                   @NotNull PrefixMatcher matcher,
                                                                   @Nullable PsiElement position) {
    final PsiElement element = candidate.getElement();
    final PsiElement context = candidate.getCurrentFileResolveContext();
    if (context instanceof GrImportStatement statement && element != null) {
      if (element instanceof PsiPackage) {
        return Collections.emptyList();
      }

      final String importedName = statement.getImportedName();
      if (importedName != null) {
        if (!(matcher.prefixMatches(importedName) ||
              element instanceof PsiMethod method && getterMatches(matcher, method, importedName) ||
              element instanceof PsiMethod psiMethod && setterMatches(matcher, psiMethod, importedName))) {
          return Collections.emptyList();
        }

        final GrCodeReferenceElement importReference = statement.getImportReference();
        if (importReference != null) {
          boolean alias = statement.isAliasedImport();
          for (GroovyResolveResult r : importReference.multiResolve(false)) {
            final PsiElement resolved = r.getElement();
            if (context.getManager().areElementsEquivalent(resolved, element) && (alias || !(element instanceof PsiClass))) {
              return generateLookupForImportedElement(candidate, importedName);
            }
            else {
              if (resolved instanceof PsiField field
                  && element instanceof PsiMethod method && GroovyPropertyUtils.isAccessorFor(method, field)) {
                return generateLookupForImportedElement(candidate, GroovyPropertyUtils.getAccessorPrefix(method)
                                                                   + GroovyPropertyUtils.capitalize(importedName));
              }
            }
          }
        }
      }
    }

    String name = element instanceof PsiNamedElement namedElement ? namedElement.getName() : element.getText();
    if (name == null || !matcher.prefixMatches(name)) {
      return Collections.emptyList();
    }

    if (element instanceof PsiClass aClass) {
      return JavaClassNameCompletionContributor
        .createClassLookupItems(aClass, afterNew, new GroovyClassNameInsertHandler(), Conditions.alwaysTrue());
    }

    LookupElementBuilder builder = LookupElementBuilder.create(element instanceof PsiPackage ? element : candidate, name);
    PsiSubstitutor substitutor = candidate.getSubstitutor();
    String typeText = getTypeText(element, substitutor, position);
    String tailText = getTailText(element, substitutor);
    GroovyResolveResultLookupElementDecorator groovyResolveResultLookupElementDecorator =
      new GroovyResolveResultLookupElementDecorator(typeText, tailText, setupLookupBuilder(element, builder, tailText, typeText));
    return Collections.singletonList(groovyResolveResultLookupElementDecorator);
  }

  private static boolean setterMatches(PrefixMatcher matcher, PsiMethod element, String importedName) {
    return GroovyPropertyUtils.isSimplePropertySetter(element) && matcher.prefixMatches(GroovyPropertyUtils.getSetterName(importedName));
  }

  private static boolean getterMatches(PrefixMatcher matcher, PsiMethod element, String importedName) {
    return GroovyPropertyUtils.isSimplePropertyGetter(element) &&
           (matcher.prefixMatches(GroovyPropertyUtils.getGetterNameNonBoolean(importedName)) ||
            PsiTypes.booleanType().equals(element.getReturnType())
            && matcher.prefixMatches(GroovyPropertyUtils.getGetterNameBoolean(importedName)));
  }

  public static LookupElement createClassLookupItem(PsiClass psiClass) {
    assert psiClass.isValid();
    return AllClassesGetter.createLookupItem(psiClass, new GroovyClassNameInsertHandler());
  }

  /**
   * Marks the given {@code LookupElement} as inaccessible.
   *
   * @param lookup the {@link LookupElement} to be marked as inaccessible
   * @return the modified {@link LookupElement} marked as inaccessible
   */
  public static @NotNull LookupElement markAsInaccessible(@NotNull LookupElement lookup) {
    return PrioritizedLookupElement.withExplicitProximity(LookupElementDecorator.withRenderer(lookup, new LookupElementRenderer<>() {
      @Override
      public void renderElement(@NotNull LookupElementDecorator<LookupElement> element, @NotNull LookupElementPresentation presentation) {
        element.getDelegate().renderElement(presentation);
        presentation.setItemTextForeground(JBColor.RED);
      }
    }), -1);
  }

  private static List<? extends LookupElement> generateLookupForImportedElement(GroovyResolveResult resolveResult,
                                                                                String importedName) {
    final PsiElement element = resolveResult.getElement();
    assert element != null;
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    LookupElementBuilder builder = LookupElementBuilder.create(resolveResult, importedName).withPresentableText(importedName);
    String typeText = getTypeText(element, substitutor, null);
    String tailText = getTailText(element, substitutor);
    LookupElementBuilder lookupBuilder =
      setupLookupBuilder(element, setupLookupBuilder(element, builder, tailText, typeText), tailText, typeText);
    GroovyResolveResultLookupElementDecorator groovyResolveResultLookupElementDecorator =
      new GroovyResolveResultLookupElementDecorator(typeText, tailText, lookupBuilder);
    return Collections.singletonList(groovyResolveResultLookupElementDecorator);
  }

  public static LookupElement createLookupElement(PsiNamedElement o) {
    return setupLookupBuilder(o, PsiSubstitutor.EMPTY, LookupElementBuilder.create(o, o.getName()), null);
  }

  public static LookupElementBuilder setupLookupBuilder(@NotNull PsiElement element,
                                                        @NotNull PsiSubstitutor substitutor,
                                                        @NotNull LookupElementBuilder builder,
                                                        @Nullable PsiElement position) {
    return setupLookupBuilder(element, builder, getTailText(element, substitutor), getTypeText(element, substitutor, position));
  }

  private static LookupElementBuilder setupLookupBuilder(@NotNull PsiElement element,
                                                         @NotNull LookupElementBuilder builder,
                                                         @Nullable String tailText,
                                                         @Nullable String typeText) {
    builder = builder.withIcon(element.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS))
      .withInsertHandler(GroovyInsertHandler.INSTANCE);
    if (element instanceof PsiModifierListOwner owner && owner.hasAnnotation(CommonClassNames.JAVA_LANG_DEPRECATED)) {
      builder = builder.strikeout();
    }
    builder = tailText != null ? builder.withTailText(tailText) : builder;
    builder = typeText != null ? builder.withTypeText(typeText) : builder;
    return builder;
  }

  private static @Nullable String getTailText(@NotNull PsiElement element, @NotNull PsiSubstitutor substitutor) {
    if (element instanceof PsiMethod method) {
      PsiClass aClass = method.getContainingClass();
      if (aClass != null && aClass.isAnnotationType()) {
        return null;
      }
      return PsiFormatUtil.formatMethod(method, substitutor, PsiFormatUtilBase.SHOW_PARAMETERS,
                                        PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE);
    }
    else if (element instanceof PsiClass psiClass) {
      String tailText = getPackageText(psiClass);
      if (substitutor.getSubstitutionMap().isEmpty() && psiClass.getTypeParameters().length > 0) {
        tailText = "<" + StringUtil.join(psiClass.getTypeParameters(), psiTypeParameter -> psiTypeParameter.getName(),
                                         "," + (showSpaceAfterComma(psiClass) ? " " : "")) + ">" + tailText;
      }
      return tailText;
    }
    return null;
  }

  private static String getPackageText(PsiClass psiClass) {
    return " (" + PsiFormatUtil.getPackageDisplayName(psiClass) + ")";
  }

  private static boolean showSpaceAfterComma(PsiClass element) {
    return CodeStyle.getLanguageSettings(element.getContainingFile(), GroovyLanguage.INSTANCE).SPACE_AFTER_COMMA;
  }

  private static @Nullable String getTypeText(@NotNull PsiElement element,
                                              @NotNull PsiSubstitutor substitutor,
                                              @Nullable PsiElement position) {
    PsiType type = switch (element) {
      case GrVariable variable -> TypeInferenceHelper.getVariableTypeInContext(position, variable);
      case PsiVariable variable -> variable.getType();
      case PsiMethod method -> substitutor.substitute(method.getReturnType());
      case GroovyProperty property -> property.getPropertyType();
      default -> null;
    };
    return type != null ? type.getPresentableText() : null;
  }

  public static boolean hasConstructorParameters(@NotNull PsiClass clazz, @NotNull PsiElement place) {
    final List<PsiMethod> constructors = getAllConstructors(clazz, place);
    final List<PsiMethod> accessibleParameterlessConstructors = new SmartList<>();
    final List<PsiMethod> inaccessibleConstructors = new SmartList<>();
    for (PsiMethod constructor : constructors) {
      if (PsiUtil.isAccessible(place, constructor)) {
        if (constructor.hasParameters()) {
          return true;
        }
        accessibleParameterlessConstructors.add(constructor);
      }
      else {
        inaccessibleConstructors.add(constructor);
      }
    }
    return accessibleParameterlessConstructors.isEmpty() &&
           (ContainerUtil.or(inaccessibleConstructors, constructor -> constructor.hasParameters())
            || ContainerUtil.or(clazz.getAllMethods(), method -> GroovyPropertyUtils.isSimplePropertySetter(method))
    );
  }

  public static void addImportForItem(PsiFile file, int startOffset, LookupElement item) {
    PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();

    Object o = item.getObject();
    if (o instanceof PsiClass aClass) {
      if (aClass.getQualifiedName() == null) return;
      final String lookupString = item.getLookupString();
      int length = lookupString.length();
      final int i = lookupString.indexOf('<');
      if (i >= 0) length = i;
      final int newOffset = addImportForClass(file, startOffset, startOffset + length, aClass);
      shortenReference(file, newOffset);
    }
    else if (o instanceof PsiType type) {
      if (type.getDeepComponentType() instanceof PsiClassType classType) {
        PsiClass refClass = classType.resolve();
        if (refClass != null) {
          int length = refClass.getName().length();
          addImportForClass(file, startOffset, startOffset + length, refClass);
        }
      }
    }
  }

  public static int addImportForClass(PsiFile file, int startOffset, int endOffset, PsiClass aClass) {
    final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    int newStartOffset = startOffset;

    final PsiReference reference = file.findReferenceAt(endOffset - 1);
    if (reference != null && reference.resolve() instanceof PsiClass psiClass
        && (psiClass.getQualifiedName() == null || file.getManager().areElementsEquivalent(aClass, psiClass))) {
      return newStartOffset;
    }

    document.replaceString(startOffset, endOffset, aClass.getName());
    final RangeMarker toDelete = JavaCompletionUtil.insertTemporary(endOffset, document, " ");
    PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();

    if (file.findReferenceAt(startOffset) instanceof GrReferenceElement<?> ref && aClass.isValid()) {
      PsiElement newElement = ref.bindToElement(aClass);
      RangeMarker marker = document.createRangeMarker(newElement.getTextRange());
      CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(newElement);
      newStartOffset = marker.getStartOffset();
    }

    if (toDelete != null && toDelete.isValid()) {
      document.deleteString(toDelete.getStartOffset(), toDelete.getEndOffset());
    }

    return newStartOffset;
  }

  //need to shorten references in type argument list
  public static void shortenReference(PsiFile file, int offset) {
    final Project project = file.getProject();
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    final Document document = manager.getDocument(file);
    assert document != null;
    manager.commitDocument(document);
    if (file.findReferenceAt(offset) instanceof GrCodeReferenceElement ref) {
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(ref);
    }
  }

  public static int addRParenth(Editor editor, int oldTail, boolean space_within_cast_parentheses) {
    int offset = -1;

    final HighlighterIterator iterator = editor.getHighlighter().createIterator(oldTail);
    while (!iterator.atEnd()) {
      final IElementType tokenType = iterator.getTokenType();
      if (TokenSets.WHITE_SPACES_OR_COMMENTS.contains(tokenType)) {
        iterator.advance();
        continue;
      }
      if (tokenType == GroovyTokenTypes.mRPAREN) {
        offset = iterator.getEnd();
      }
      break;
    }
    if (offset != -1) return offset;
    offset = oldTail;
    if (space_within_cast_parentheses) {
      offset = TailType.insertChar(editor, oldTail, ' ');
    }
    return TailType.insertChar(editor, offset, ')');
  }

  public static boolean skipDefGroovyMethod(GrGdkMethod gdkMethod, PsiSubstitutor substitutor, @Nullable PsiType type) {
    if (type == null) return false;
    String name = gdkMethod.getStaticMethod().getName();

    final PsiType baseType = gdkMethod.getReceiverType();
    if (!TypeConversionUtil.erasure(baseType).equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return false;

    if (GdkMethodUtil.COLLECTION_METHOD_NAMES.contains(name)) {
      final PsiType substituted = substitutor != null ? substitutor.substitute(baseType) : baseType;
      return !(type instanceof PsiArrayType ||
               InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ITERABLE) ||
               substituted instanceof PsiArrayType ||
               InheritanceUtil.isInheritor(substituted, CommonClassNames.JAVA_LANG_ITERABLE));
    }
    return !GdkMethodUtil.isWithName(name);
  }

  /*
  we are here:  foo(List<? <caret> ...
   */
  public static boolean isWildcardCompletion(PsiElement position) {
    PsiElement prev = PsiUtil.getPreviousNonWhitespaceToken(position);
    if (prev instanceof PsiErrorElement) prev = PsiUtil.getPreviousNonWhitespaceToken(prev);

    if (prev == null || prev.getNode().getElementType() != GroovyTokenTypes.mQUESTION) return false;

    final PsiElement pprev = PsiUtil.getPreviousNonWhitespaceToken(prev);
    if (pprev == null) return false;

    final IElementType t = pprev.getNode().getElementType();
    return t == GroovyTokenTypes.mLT || t == GroovyTokenTypes.mCOMMA;
  }

  static boolean isNewStatementInScript(PsiElement context) {
    final PsiElement leaf = getLeafByOffset(context.getTextRange().getStartOffset() - 1, context);
    return leaf != null && isNewStatement(context, false) && leaf.getParent() instanceof GroovyFile;
  }

  public static boolean isReferenceElementInNewExpr(PsiElement context) {
    return context.getParent() instanceof GrCodeReferenceElement ref && ref.getParent() instanceof GrNewExpression;
  }

  static boolean isCodeReferenceElementApplicableToModifierCompletion(PsiElement context) {
    if (context.getParent() instanceof GrCodeReferenceElement ref) {
      PsiElement parent = ref.getParent();
      return !(parent instanceof GrImportStatement) && !(parent instanceof GrPackageDefinition) && !(parent instanceof GrNewExpression);
    }
    return false;
  }

  static boolean isTypelessParameter(PsiElement context) {
    return context.getParent() instanceof GrParameter parameter && parameter.getTypeElementGroovy() == null;
  }

  public static boolean isTupleVarNameWithoutTypeDeclared(PsiElement position) {
    return position.getParent() instanceof GrVariable variable &&
           variable.getNameIdentifierGroovy() == position &&
           variable.getTypeElementGroovy() == null &&
           variable.getParent() instanceof GrVariableDeclaration declaration &&
           declaration.isTuple();
  }

  public static void processVariants(GrReferenceElement<?> referenceElement,
                                     PrefixMatcher matcher,
                                     CompletionParameters parameters,
                                     Consumer<LookupElement> consumer) {
    if (referenceElement instanceof GrCodeReferenceElementImpl) {
      CompleteCodeReferenceElement.complete((GrCodeReferenceElement)referenceElement, matcher, consumer);
    }
    else if (referenceElement instanceof GrReferenceExpressionImpl expression) {
      CompleteReferenceExpression.processVariants(matcher, consumer, expression, parameters);
    }
  }

  public static boolean isInPossibleClosureParameter(PsiElement position) { //Closure cl={String x, <caret>...
    if (position == null) return false;

    if (position instanceof PsiWhiteSpace || position.getNode().getElementType() == GroovyTokenTypes.mNLS) {
      position = FilterPositionUtil.searchNonSpaceNonCommentBack(position);
    }

    boolean hasCommas = false;
    while (position != null) {
      PsiElement parent = position.getParent();
      if (parent instanceof GrVariable) {
        PsiElement prev = FilterPositionUtil.searchNonSpaceNonCommentBack(parent);
        hasCommas = prev != null && prev.getNode().getElementType() == GroovyTokenTypes.mCOMMA;
      }

      if (parent instanceof GrClosableBlock) {
        PsiElement sibling = position.getPrevSibling();
        while (sibling != null) {
          if (sibling instanceof GrParameterList) {
            return hasCommas;
          }

          boolean isComma = sibling instanceof LeafPsiElement element && GroovyTokenTypes.mCOMMA == element.getElementType();
          hasCommas |= isComma;

          if (isComma ||
              sibling instanceof PsiWhiteSpace ||
              sibling instanceof PsiErrorElement ||
              sibling instanceof GrVariableDeclaration ||
              sibling instanceof GrReferenceExpression expression && !expression.isQualified()) {
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

  public static @Nullable PsiType getQualifierType(@Nullable PsiElement qualifier) {
    PsiType qualifierType = qualifier instanceof GrExpression expression ? expression.getType() : null;
    if (ResolveUtil.resolvesToClass(qualifier)) {
      PsiType type = ResolveUtil.unwrapClassType(qualifierType);
      if (type != null) {
        qualifierType = type;
      }
    }
    return qualifierType;
  }

  static boolean canResolveToPackage(@NotNull PsiElement qualifier) {
    return qualifier instanceof GrReferenceExpression && resolvePackageFqn((GrReferenceElement<?>)qualifier) != null;
  }
}
