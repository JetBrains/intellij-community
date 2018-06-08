// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
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
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementContext;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class StructuralSearchProfile {
  public static final ExtensionPointName<StructuralSearchProfile> EP_NAME =
    ExtensionPointName.create("com.intellij.structuralsearch.profile");

  public abstract void compile(PsiElement[] elements, @NotNull GlobalCompilingVisitor globalVisitor);

  @NotNull
  public abstract PsiElementVisitor createMatchingVisitor(@NotNull GlobalMatchingVisitor globalVisitor);

  @NotNull
  public abstract NodeFilter getLexicalNodesFilter();

  @NotNull
  public abstract CompiledPattern createCompiledPattern();

  public List<MatchPredicate> getCustomPredicates(MatchVariableConstraint constraint, String name, MatchOptions options) {
    return Collections.emptyList();
  }

  public abstract boolean isMyLanguage(@NotNull Language language);

  @NotNull
  public PsiElement[] createPatternTree(@NotNull String text,
                                        @NotNull PatternTreeContext context,
                                        @NotNull FileType fileType,
                                        @Nullable Language language,
                                        @Nullable String contextName,
                                        @Nullable String extension,
                                        @NotNull Project project,
                                        boolean physical) {
    final String ext = extension != null ? extension : fileType.getDefaultExtension();
    final String name = "__dummy." + ext;
    final PsiFileFactory factory = PsiFileFactory.getInstance(project);

    final PsiFile file = language == null
                         ? factory.createFileFromText(name, fileType, text, LocalTimeCounter.currentTime(), physical, true)
                         : factory.createFileFromText(name, language, text, physical, true);

    return file != null ? file.getChildren() : PsiElement.EMPTY_ARRAY;
  }

  @NotNull
  public PsiElement[] createPatternTree(@NotNull String text,
                                        @NotNull PatternTreeContext context,
                                        @NotNull FileType fileType,
                                        @NotNull Project project,
                                        boolean physical) {
    return createPatternTree(text, context, fileType, null, null, null, project, physical);
  }

  @NotNull
  public Document createDocument(@NotNull Project project, @NotNull FileType fileType, Language dialect, String text) {
    PsiFile codeFragment = createCodeFragment(project, text, null);
    if (codeFragment == null) {
      codeFragment = createFileFragment(project, fileType, dialect, text);
    }

    if (codeFragment != null) {
      final Document doc = PsiDocumentManager.getInstance(project).getDocument(codeFragment);
      assert doc != null : "code fragment element should be physical";
      //DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(codeFragment, false);
      return doc;
    }

    return EditorFactory.getInstance().createDocument(text);
  }

  @NotNull
  public Editor createEditor(@NotNull Project project,
                             @NotNull FileType fileType,
                             Language dialect,
                             String text) {
    PsiFile codeFragment = createCodeFragment(project, text, null);
    if (codeFragment == null) {
      codeFragment = createFileFragment(project, fileType, dialect, text);
    }

    if (codeFragment != null) {
      final Document doc = PsiDocumentManager.getInstance(project).getDocument(codeFragment);
      assert doc != null : "code fragment element should be physical";
      DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(codeFragment, false);
      return UIUtil.createEditor(doc, project, true, true, getTemplateContextType());
    }

    final EditorFactory factory = EditorFactory.getInstance();
    final Document document = factory.createDocument(text);
    final EditorEx editor = (EditorEx)factory.createEditor(document, project);
    editor.getSettings().setFoldingOutlineShown(false);
    return editor;
  }

  private static PsiFile createFileFragment(Project project, FileType fileType, Language dialect, String text) {
    final String name = "__dummy." + fileType.getDefaultExtension();
    final PsiFileFactory factory = PsiFileFactory.getInstance(project);

    return dialect == null ?
           factory.createFileFromText(name, fileType, text, LocalTimeCounter.currentTime(), true, true) :
           factory.createFileFromText(name, dialect, text, true, true);
  }

  @Nullable
  public PsiCodeFragment createCodeFragment(Project project, String text, @Nullable PsiElement context) {
    return null;
  }

  @NotNull
  public abstract Class<? extends TemplateContextType> getTemplateContextTypeClass();

  public final TemplateContextType getTemplateContextType() {
    final Class<? extends TemplateContextType> clazz = getTemplateContextTypeClass();
    return ContainerUtil.findInstance(TemplateContextType.EP_NAME.getExtensions(), clazz);
  }

  @Nullable
  public FileType detectFileType(@NotNull PsiElement context) {
    return null;
  }

  @Nullable
  public StructuralReplaceHandler getReplaceHandler(@NotNull ReplacementContext context) {
    return null;
  }

  public void checkSearchPattern(CompiledPattern pattern) {
  }

  public void checkReplacementPattern(Project project, ReplaceOptions options) {
    String fileType = options.getMatchOptions().getFileType().getName().toLowerCase();
    throw new UnsupportedPatternException(SSRBundle.message("replacement.not.supported.for.filetype", fileType));
  }

  // only for nodes not filtered by lexical-nodes filter; they can be by default
  public boolean canBeVarDelimeter(@NotNull PsiElement element) {
    return false;
  }

  public String getText(PsiElement match, int start, int end) {
    final String matchText = match.getText();
    if (start==0 && end==-1) return matchText;
    return matchText.substring(start, end == -1 ? matchText.length() : end);
  }

  @NotNull
  public String getTypedVarString(PsiElement element) {
    if (element instanceof PsiNamedElement) {
      final String name = ((PsiNamedElement)element).getName();
      if (name != null) {
        return name;
      }
    }
    return element.getText();
  }
  
  public String getMeaningfulText(PsiElement element) {
    return getTypedVarString(element);
  }

  public PsiElement updateCurrentNode(PsiElement node) {
    return node;
  }

  public PsiElement extendMatchedByDownUp(PsiElement node) {
    return node;
  }

  public PsiElement extendMatchOnePsiFile(PsiElement file) {
    return file;
  }

  public LanguageFileType getDefaultFileType(@Nullable LanguageFileType fileType) {
    return fileType;
  }

  public Configuration[] getPredefinedTemplates() {
    return Configuration.EMPTY_ARRAY;
  }

  public void provideAdditionalReplaceOptions(@NotNull PsiElement node, ReplaceOptions options, ReplacementBuilder builder) {}

  public int handleSubstitution(final ParameterInfo info,
                                MatchResult match,
                                StringBuilder result,
                                int offset,
                                ReplacementInfo replacementInfo) {
    if (info.getName().equals(match.getName())) {
      final String replacementString;
      boolean removeSemicolon = false;
      if (match.hasChildren() && !match.isScopeMatch()) {
        // compound matches
        StringBuilder buf = new StringBuilder();

        for (final MatchResult matchResult : match.getChildren()) {
          final PsiElement currentElement = matchResult.getMatch();

          if (buf.length() > 0) {
            if (info.isArgumentContext()) {
              buf.append(',');
            } else {
              final PsiElement sibling = currentElement.getPrevSibling();
              buf.append(sibling instanceof PsiWhiteSpace ? sibling.getText() : " ");
            }
          }

          buf.append(matchResult.getMatchImage());
          removeSemicolon = currentElement instanceof PsiComment;
        }
        replacementString = buf.toString();
      } else {
        if (info.isStatementContext()) {
          removeSemicolon = match.getMatch() instanceof PsiComment;
        }
        replacementString = match.getMatchImage();
      }

      offset = Replacer.insertSubstitution(result, offset, info, replacementString);
      if (info.isStatementContext() &&
           (removeSemicolon || StringUtil.endsWithChar(replacementString, ';') || StringUtil.endsWithChar(replacementString, '}'))) {
        final int start = info.getStartIndex() + offset;
        result.delete(start, start + 1);
        offset--;
      }
    }
    return offset;
  }

  public int handleNoSubstitution(ParameterInfo info, int offset, StringBuilder result) {
    if (info.isHasCommaBefore()) {
      result.delete(info.getBeforeDelimiterPos() + offset, info.getBeforeDelimiterPos() + 1 + offset);
      --offset;
    }
    else if (info.isHasCommaAfter()) {
      result.delete(info.getAfterDelimiterPos() + offset, info.getAfterDelimiterPos() + 1 + offset);
      --offset;
    }
    return offset;
  }

  @Contract("null -> false")
  public boolean isIdentifier(@Nullable PsiElement element) {
    return false;
  }

  @NotNull
  public Collection<String> getReservedWords() {
    return Collections.emptySet();
  }

  public boolean isDocCommentOwner(PsiElement match) {
    return false;
  }

  @Contract("!null -> !null")
  public PsiElement getPresentableElement(PsiElement element) {
    return isIdentifier(element) ? element.getParent() : element;
  }

  /**
   * Override this method to influence which UI controls are shown when editing the constraints of the specified variable.
   *
   * @param constraintName  the name of the constraint controls for which applicability is considered.
   *  See {@link com.intellij.structuralsearch.plugin.ui.UIUtil} for predefined constraint names
   * @param variableNode  the psi element corresponding to the current variable
   * @param completePattern  true, if the current variableNode encompasses the complete pattern. The variableNode can also be null in this case.
   * @param target  true, if the current variableNode is the target of the search
   * @return true, if the requested constraint is applicable and the corresponding UI should be shown when editing the variable; false otherwise
   */
  public boolean isApplicableConstraint(String constraintName, @Nullable PsiElement variableNode, boolean completePattern, boolean target) {
    switch (constraintName) {
      case UIUtil.MINIMUM_ZERO:
        if (target) return false;
      case UIUtil.MAXIMUM_UNLIMITED:
      case UIUtil.TEXT:
      case UIUtil.REFERENCE: return !completePattern;
    }
    return false;
  }

  public final boolean isApplicableConstraint(String constraintName, List<PsiElement> nodes, boolean completePattern, boolean target) {
    if (nodes.isEmpty()) {
      return isApplicableConstraint(constraintName, (PsiElement)null, completePattern, target);
    }
    boolean result = true;
    for (PsiElement node : nodes) {
      result &= isApplicableConstraint(constraintName, node, completePattern, target);
    }
    return result;
  }
}
