// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.predicates.MatchPredicate;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.impl.ParameterInfo;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementBuilder;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.ConfigurationManager;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Entry point for supporting a specific language in Structural Search.
 */
public abstract class StructuralSearchProfile {
  public static final ExtensionPointName<StructuralSearchProfile> EP_NAME =
    ExtensionPointName.create("com.intellij.structuralsearch.profile");
  @NonNls protected static final String PATTERN_PLACEHOLDER = "$$PATTERN_PLACEHOLDER$$";
  private Boolean myReplaceSupported;

  /**
   * Creates the pattern PSI tree which is stored inside CompiledPattern.
   * Uses compiling visitor to visit the query PsiElements, sets the correct Filters and Handlers.
   * @see #createCompiledPattern()
   */
  public abstract void compile(PsiElement @NotNull [] elements, @NotNull GlobalCompilingVisitor globalVisitor);

  /**
   * The MatchingVisitor knows how to match language specific constructs, when those constructs have already been found.
   *
   * <p>For example {@code if} statements in Java: first the condition of the pattern is compared to the condition of the found
   * {@code if} statement. If it matches, compare the then part of the {@code if} statement. And if the pattern has an
   * {@code else} part, try to match that as well. If no {@code else} is present in the pattern, just ignore any {@code else} in the code.
   *
   * <p>In some cases MatchingVisitor also knows how to match two not quite similar things as well,
   * like {@code String s = "";}  and {@code var s = "";} in Java, if {@code s} has the same inferred type as the explicit type in
   * the pattern.
   *
   * @param globalVisitor  the global matching visitor which the created matching visitor can use to e.g. retrieve the current element to match.
   * @return a language specific matching visitor
   */
  @NotNull
  public abstract PsiElementVisitor createMatchingVisitor(@NotNull GlobalMatchingVisitor globalVisitor);

  /**
   * @return {@code true} for elements that should be matched. Usually {@code false} for white space and error elements.
   */
  public boolean isMatchNode(PsiElement element) {
    return !(element instanceof PsiWhiteSpace) && !(element instanceof PsiErrorElement);
  }

  /**
   * Creates language specific compiled pattern.
   */
  @NotNull
  public abstract CompiledPattern createCompiledPattern();

  @NotNull
  public List<MatchPredicate> getCustomPredicates(@NotNull MatchVariableConstraint constraint, @NotNull String name, @NotNull MatchOptions options) {
    return Collections.emptyList();
  }

  /**
   * @return true, if this structural search profile can match code of the specified language. False otherwise.
   */
  public abstract boolean isMyLanguage(@NotNull Language language);

  /**
   * Converts query text into PSI tree.
   * @param text  the text of the search query.
   */
  @NotNull
  public PsiElement @NotNull [] createPatternTree(@NotNull String text,
                                                  @NotNull PatternTreeContext context,
                                                  @NotNull LanguageFileType fileType,
                                                  @NotNull Language language,
                                                  @Nullable String contextId,
                                                  @NotNull Project project,
                                                  boolean physical) {
    return doCreatePatternTree(text, context, fileType, language, project, physical, getContext(text, language, contextId));
  }

  @NotNull
  public PsiElement @NotNull [] createPatternTree(@NotNull String text,
                                                  @NotNull PatternContextInfo contextInfo,
                                                  @NotNull LanguageFileType fileType,
                                                  @NotNull Language language,
                                                  @NotNull Project project,
                                                  boolean physical) {
    String contextConstraint = contextInfo.getContextConstraint();
    if (StringUtil.isEmpty(contextConstraint)) {
      PatternContext patternContext = contextInfo.getPatternContext();
      String contextId = patternContext != null ? patternContext.getId() : null;
      return createPatternTree(text, contextInfo.getTreeContext(), fileType, language, contextId, project, physical);
    }
    return doCreatePatternTree(text, contextInfo.getTreeContext(), fileType, language, project, physical, getContextByConstraint(contextConstraint, project));
  }

  @NotNull
  private PsiElement @NotNull [] doCreatePatternTree(@NotNull String text,
                                                     @NotNull PatternTreeContext context,
                                                     @NotNull LanguageFileType fileType,
                                                     @NotNull Language language,
                                                     @NotNull Project project,
                                                     boolean physical,
                                                     @NotNull String strContext) {
    String placeholderName = getPlaceholderVarName();
    final String patternInContext = (context == PatternTreeContext.File) ? text : strContext.replace(placeholderName, text);

    @NonNls final String name = "__dummy." + fileType.getDefaultExtension();
    final PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(name, language, patternInContext, physical, true);
    if (file == null) {
      return PsiElement.EMPTY_ARRAY;
    }
    if (context == PatternTreeContext.File) {
      return new PsiElement[] {file};
    }

    final int offset = strContext.indexOf(placeholderName);
    PsiElement element = file.findElementAt(offset);
    if (element == null) {
      return PsiElement.EMPTY_ARRAY;
    }

    PsiElement topElement = element;
    element = element.getParent();

    final int patternLength = text.length();
    while (element != null) {
      if (element.getTextRange().getStartOffset() == offset && element.getTextLength() <= patternLength) {
        topElement = element;
      }
      element = element.getParent();
    }

    if (topElement instanceof PsiFile) {
      return topElement.getChildren();
    }

    final List<PsiElement> result = new SmartList<>();
    result.add(topElement);
    topElement = topElement.getNextSibling();

    final int endOffset = offset + patternLength;
    while (topElement != null && topElement.getTextRange().getEndOffset() <= endOffset) {
      result.add(topElement);
      topElement = topElement.getNextSibling();
    }

    return result.toArray(PsiElement.EMPTY_ARRAY);
  }

  @NotNull
  public List<PatternContext> getPatternContexts() {
    return Collections.emptyList();
  }

  @NotNull
  protected String getPlaceholderVarName() {
    return PATTERN_PLACEHOLDER;
  }

  @NotNull
  protected String getContext(@NotNull String pattern, @Nullable Language language, @Nullable String contextId) {
    return getPlaceholderVarName();
  }

  @NotNull
  private String getContextByConstraint(@NotNull String contextConstraint, @NotNull Project project) {
    Configuration configuration = ConfigurationManager.getInstance(project).findConfigurationByName(contextConstraint);
    return configuration != null ? configuration.getMatchOptions().getSearchPattern() : getPlaceholderVarName();
  }

  @Nullable
  public PsiCodeFragment createCodeFragment(@NotNull Project project, @NotNull String text, @Nullable String contextId) {
    return null;
  }

  /**
   * This method is called while holding a read action.
   */
  @NotNull
  public String getCodeFragmentText(@NotNull PsiFile fragment) {
    return fragment.getText();
  }

  @NotNull
  public abstract Class<? extends TemplateContextType> getTemplateContextTypeClass();

  @Nullable
  public LanguageFileType detectFileType(@NotNull PsiElement context) {
    return null;
  }

  @Nullable
  public StructuralReplaceHandler getReplaceHandler(@NotNull Project project, @NotNull ReplaceOptions replaceOptions) {
    return null;
  }

  /**
   * @return true, if this structural search profile can shorten fully qualified names when replacing.
   */
  public boolean supportsShortenFQNames() {
    return false;
  }

  /**
   * @return true, if this structural search profile can use static imports when replacing.
   */
  public boolean supportsUseStaticImports() {
    return false;
  }

  public void checkSearchPattern(@NotNull CompiledPattern pattern) {}

  public void checkReplacementPattern(@NotNull Project project, @NotNull ReplaceOptions options) {
    if (isReplaceSupported()) return;
    final LanguageFileType fileType = options.getMatchOptions().getFileType();
    if (fileType == null) return;
    final String fileTypeName = StringUtil.toLowerCase(fileType.getName());
    throw new UnsupportedPatternException(SSRBundle.message("replacement.not.supported.for.filetype", fileTypeName));
  }

  private boolean isReplaceSupported() {
    if (myReplaceSupported != null) return myReplaceSupported;
    Class<?> declaringClass = ReflectionUtil.getMethodDeclaringClass(getClass(), "getReplaceHandler", Project.class, ReplaceOptions.class);
    return myReplaceSupported = !StructuralSearchProfile.class.equals(declaringClass);
  }

  public boolean shouldShowProblem(@NotNull PsiErrorElement error) {
    return false;
  }

  // only for nodes not filtered by lexical-nodes filter; they can be by default
  public boolean canBeVarDelimiter(@NotNull PsiElement element) {
    return false;
  }

  @NotNull
  public String getText(@NotNull PsiElement match, int start, int end) {
    final String matchText = match.getText();
    if (start == 0 && end == -1) return matchText;
    return matchText.substring(start, end == -1 ? matchText.length() : end);
  }

  @NotNull
  public String getTypedVarString(@NotNull PsiElement element) {
    if (element instanceof PsiNamedElement) {
      final String name = ((PsiNamedElement)element).getName();
      if (name != null) {
        return name;
      }
    }
    return element.getText();
  }

  public String getMeaningfulText(@NotNull PsiElement element) {
    return getTypedVarString(element);
  }

  public String getAlternativeText(@NotNull PsiElement element, @NotNull String previousText) {
    return null;
  }

  @NotNull
  public PsiElement updateCurrentNode(@NotNull PsiElement node) {
    return node;
  }

  @NotNull
  public PsiElement extendMatchedByDownUp(@NotNull PsiElement node) {
    return node;
  }

  public LanguageFileType getDefaultFileType(@Nullable LanguageFileType fileType) {
    return fileType;
  }

  public Configuration @NotNull [] getPredefinedTemplates() {
    return Configuration.EMPTY_ARRAY;
  }

  public void provideAdditionalReplaceOptions(@NotNull PsiElement node, @NotNull ReplaceOptions options, @NotNull ReplacementBuilder builder) {}

  public void handleSubstitution(@NotNull ParameterInfo info, @NotNull MatchResult match, @NotNull StringBuilder result, @NotNull ReplacementInfo replacementInfo) {
    if (info.getName().equals(match.getName())) {
      final String replacementString;
      boolean removeSemicolon = false;
      if (match.hasChildren() && !match.isScopeMatch()) {
        // compound matches
        final StringBuilder buf = new StringBuilder();

        for (final MatchResult matchResult : match.getChildren()) {
          final PsiElement currentElement = matchResult.getMatch();

          if (buf.length() > 0) {
            if (info.isArgumentContext()) {
              buf.append(',');
            }
            else {
              final PsiElement sibling = currentElement.getPrevSibling();
              buf.append(sibling instanceof PsiWhiteSpace ? sibling.getText() : " ");
            }
          }

          buf.append(matchResult.getMatchImage());
          removeSemicolon = currentElement instanceof PsiComment;
        }
        replacementString = buf.toString();
      }
      else {
        if (info.isStatementContext()) {
          removeSemicolon = match.getMatch() instanceof PsiComment;
        }
        replacementString = match.getMatchImage();
      }

      int offset = Replacer.insertSubstitution(result, 0, info, replacementString);
      if (info.isStatementContext() &&
          (removeSemicolon || StringUtil.endsWithChar(replacementString, ';') || StringUtil.endsWithChar(replacementString, '}'))) {
        final int start = info.getStartIndex() + offset;
        result.delete(start, start + 1);
      }
    }
  }

  public void handleNoSubstitution(@NotNull ParameterInfo info, @NotNull StringBuilder result) {
    if (info.isHasCommaBefore()) {
      result.delete(info.getBeforeDelimiterPos(), info.getBeforeDelimiterPos() + 1);
    }
    else if (info.isHasCommaAfter()) {
      result.delete(info.getAfterDelimiterPos(), info.getAfterDelimiterPos() + 1);
    }
    else if (info.getStartIndex() < result.length() && StringUtil.isLineBreak(result.charAt(info.getStartIndex()))) {
      result.deleteCharAt(info.getStartIndex()); // delete line break when count filter matches nothing
    }
  }

  @Contract("null -> false")
  public boolean isIdentifier(@Nullable PsiElement element) {
    return false;
  }

  @NotNull
  public Collection<String> getReservedWords() {
    return Collections.emptySet();
  }

  public boolean isDocCommentOwner(@NotNull PsiElement match) {
    return false;
  }

  @NotNull
  public PsiElement getPresentableElement(@NotNull PsiElement element) {
    if (isIdentifier(element)) {
      final PsiElement parent = element.getParent();
      if (parent != null) return parent;
    }
    return element;
  }

  /**
   * Override this method to influence which UI controls are shown when editing the constraints of the specified variable.
   *
   * @param constraintName  the name of the constraint controls for which applicability is considered.
   *                        See {@link UIUtil} for predefined constraint names
   * @param variableNode    the psi element corresponding to the current variable
   * @param completePattern true, if the current variableNode encompasses the complete pattern. The variableNode can also be null in this case.
   * @param target          true, if the current variableNode is the target of the search
   * @return true, if the requested constraint is applicable and the corresponding UI should be shown when editing the variable; false otherwise
   */
  public boolean isApplicableConstraint(@NotNull String constraintName, @Nullable PsiElement variableNode, boolean completePattern, boolean target) {
    switch (constraintName) {
      case UIUtil.MINIMUM_ZERO:
        if (target) return false;
      case UIUtil.MAXIMUM_UNLIMITED:
      case UIUtil.TEXT:
      case UIUtil.REFERENCE:
        return !completePattern;
    }
    return false;
  }

  public final boolean isApplicableConstraint(@NotNull String constraintName,
                                              @NotNull List<? extends PsiElement> nodes,
                                              boolean completePattern,
                                              boolean target) {
    if (nodes.isEmpty()) {
      return isApplicableConstraint(constraintName, (PsiElement)null, completePattern, target);
    }
    boolean result = true;
    for (PsiElement node : nodes) {
      result &= isApplicableConstraint(constraintName, node, completePattern, target);
    }
    return result;
  }

  public boolean isApplicableContextConfiguration(@NotNull Configuration configuration) {
    return !configuration.isPredefined();
  }

  public boolean isReplacementTypedVariable(@NotNull String name) {
    return name.length() > 1 && name.charAt(0) == '$' && name.charAt(name.length() - 1) == '$';
  }

  @NotNull
  public String compileReplacementTypedVariable(@NotNull String name) {
    return "$" + name + "$";
  }

  @NotNull
  public String stripReplacementTypedVariableDecorations(@NotNull String name) {
    return name.substring(1, name.length() - 1);
  }
}
