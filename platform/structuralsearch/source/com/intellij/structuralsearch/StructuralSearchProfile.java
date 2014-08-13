package com.intellij.structuralsearch;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.impl.ParameterInfo;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementBuilder;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementContext;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

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
  public abstract PsiElementVisitor getLexicalNodesFilter(@NotNull LexicalNodesFilter filter);

  @NotNull
  public abstract CompiledPattern createCompiledPattern();

  public static String getTypeName(FileType fileType) {
    return fileType.getName().toLowerCase();
  }

  public abstract boolean canProcess(@NotNull FileType fileType);

  public abstract boolean isMyLanguage(@NotNull Language language);

  public boolean isMyFile(PsiFile file, @NotNull Language language, Language... patternLanguages) {
    if (isMyLanguage(language) && ArrayUtil.find(patternLanguages, language) >= 0) {
      return true;
    }
    return false;
  }

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
  public Editor createEditor(@NotNull SearchContext searchContext,
                             @NotNull FileType fileType,
                             Language dialect,
                             String text,
                             boolean useLastConfiguration) {
    PsiFile codeFragment = createCodeFragment(searchContext.getProject(), text, null);
    if (codeFragment == null) {
      codeFragment = createFileFragment(searchContext, fileType, dialect, text);
    }

    if (codeFragment != null) {
      final Document doc = PsiDocumentManager.getInstance(searchContext.getProject()).getDocument(codeFragment);
      assert doc != null : "code fragment element should be physical";
      DaemonCodeAnalyzer.getInstance(searchContext.getProject()).setHighlightingEnabled(codeFragment, false);
      return UIUtil.createEditor(doc, searchContext.getProject(), true, true, getTemplateContextType());
    }

    final EditorFactory factory = EditorFactory.getInstance();
    final Document document = factory.createDocument(text);
    final EditorEx editor = (EditorEx)factory.createEditor(document, searchContext.getProject());
    editor.getSettings().setFoldingOutlineShown(false);
    return editor;
  }

  private static PsiFile createFileFragment(SearchContext searchContext, FileType fileType, Language dialect, String text) {
    final String name = "__dummy." + fileType.getDefaultExtension();
    final PsiFileFactory factory = PsiFileFactory.getInstance(searchContext.getProject());

    return dialect == null ?
           factory.createFileFromText(name, fileType, text, LocalTimeCounter.currentTime(), true, true) :
           factory.createFileFromText(name, dialect, text, true, true);
  }

  @Nullable
  public PsiCodeFragment createCodeFragment(Project project, String text, @Nullable PsiElement context) {
    return null;
  }

  @Nullable
  public Class<? extends TemplateContextType> getTemplateContextTypeClass() {
    return null;
  }

  public final TemplateContextType getTemplateContextType() {
    final Class<? extends TemplateContextType> clazz = getTemplateContextTypeClass();
    return clazz != null ? ContainerUtil.findInstance(TemplateContextType.EP_NAME.getExtensions(), clazz) : null;
  }

  @Nullable
  public FileType detectFileType(@NotNull PsiElement context) {
    return null;
  }

  @Nullable
  public StructuralReplaceHandler getReplaceHandler(@NotNull ReplacementContext context) {
    return null;
  }

  public void checkSearchPattern(Project project, MatchOptions options) {
  }

  public void checkReplacementPattern(Project project, ReplaceOptions options) {
    String fileType = getTypeName(options.getMatchOptions().getFileType());
    throw new UnsupportedPatternException(SSRBundle.message("replacement.not.supported.for.filetype", fileType));
  }

  @NotNull
  public Language getLanguage(PsiElement element) {
    return element.getLanguage();
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

  public Class getElementContextByPsi(PsiElement element) {
    return element.getClass();
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

  Configuration[] getPredefinedTemplates() {
    return Configuration.EMPTY_ARRAY;
  }

  public void provideAdditionalReplaceOptions(@NotNull PsiElement node, ReplaceOptions options, ReplacementBuilder builder) {}

  public int handleSubstitution(final ParameterInfo info,
                                MatchResult match,
                                StringBuilder result,
                                int offset,
                                HashMap<String, MatchResult> matchMap) {
    return defaultHandleSubstitution(info, match, result, offset);
  }

  public static int defaultHandleSubstitution(ParameterInfo info, MatchResult match, StringBuilder result, int offset) {
    if (info.getName().equals(match.getName())) {
      String replacementString = match.getMatchImage();
      boolean forceAddingNewLine = false;
      if (match.getAllSons().size() > 0 && !match.isScopeMatch()) {
        // compound matches
        StringBuilder buf = new StringBuilder();

        for (final MatchResult matchResult : match.getAllSons()) {
          final PsiElement currentElement = matchResult.getMatch();
          
          if (buf.length() > 0) {
            if (info.isArgumentContext()) {
              buf.append(',');
            } else {
              buf.append(' ');
            }
          }

          buf.append(matchResult.getMatchImage());
          forceAddingNewLine = currentElement instanceof PsiComment;
        }
        replacementString = buf.toString();
      } else {
        if (info.isStatementContext()) {
          forceAddingNewLine = match.getMatch() instanceof PsiComment;
        }
      }

      offset = Replacer.insertSubstitution(result, offset, info, replacementString);
      if (forceAddingNewLine && info.isStatementContext()) {
        result.insert(info.getStartIndex() + offset + 1, '\n');
        offset ++;
      }
    }
    return offset;
  }

  public int processAdditionalOptions(ParameterInfo info, int offset, StringBuilder result, MatchResult r) {
    return offset;
  }

  public boolean isIdentifier(PsiElement element) {
    return false;
  }

  public Collection<String> getReservedWords() {
    return Collections.emptySet();
  }

  public boolean isDocCommentOwner(PsiElement match) {
    return false;
  }
}
