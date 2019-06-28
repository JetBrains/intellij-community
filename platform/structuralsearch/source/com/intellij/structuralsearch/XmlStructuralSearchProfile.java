// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.XmlContextType;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.xml.*;
import com.intellij.structuralsearch.impl.matcher.*;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.compiler.XmlCompilingVisitor;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacerUtil;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.structuralsearch.PredefinedConfigurationUtil.createSearchTemplateInfo;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlStructuralSearchProfile extends StructuralSearchProfile {

  @Override
  public void compile(PsiElement[] elements, @NotNull GlobalCompilingVisitor globalVisitor) {
    new XmlCompilingVisitor(globalVisitor).compile(elements);
  }

  @Override
  @NotNull
  public PsiElementVisitor createMatchingVisitor(@NotNull GlobalMatchingVisitor globalVisitor) {
    return new XmlMatchingVisitor(globalVisitor);
  }

  @Override
  public boolean isIdentifier(@Nullable PsiElement element) {
    return element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_NAME;
  }

  @NotNull
  @Override
  public String getTypedVarString(PsiElement element) {
    return element instanceof XmlText ? element.getText().trim() : super.getTypedVarString(element);
  }

  @NotNull
  @Override
  public NodeFilter getLexicalNodesFilter() {
    return element -> {
      if (element instanceof XmlText) {
        final PsiElement child = element.getFirstChild();
        return child == element.getLastChild() && child instanceof PsiWhiteSpace;
      }
      return element instanceof PsiWhiteSpace || element instanceof PsiErrorElement;
    };
  }

  @Override
  @NotNull
  public CompiledPattern createCompiledPattern() {
    return new XmlCompiledPattern();
  }

  @Override
  public boolean isMyLanguage(@NotNull Language language) {
    return language instanceof XMLLanguage;
  }

  @NotNull
  @Override
  public PsiElement[] createPatternTree(@NotNull String text,
                                        @NotNull PatternTreeContext context,
                                        @NotNull LanguageFileType fileType,
                                        @NotNull Language language,
                                        String contextId,
                                        @NotNull Project project,
                                        boolean physical) {
    text = context == PatternTreeContext.File ? text : "<QQQ>" + text + "</QQQ>";
    final String fileName = "dummy." + fileType.getDefaultExtension();
    final PsiFile fileFromText =
      PsiFileFactory.getInstance(project).createFileFromText(fileName, fileType, text, LocalTimeCounter.currentTime(), physical, true);

    final XmlDocument document = HtmlUtil.getRealXmlDocument(((XmlFile)fileFromText).getDocument());
    if (context == PatternTreeContext.File) {
      return new PsiElement[]{document};
    }

    return document.getRootTag().getValue().getChildren();
  }

  @NotNull
  @Override
  public Class<? extends TemplateContextType> getTemplateContextTypeClass() {
    return XmlContextType.class;
  }

  @NotNull
  @Override
  public LanguageFileType detectFileType(@NotNull PsiElement context) {
    final PsiFile file = context instanceof PsiFile ? (PsiFile)context : context.getContainingFile();
    final Language contextLanguage = context instanceof PsiFile ? null : context.getLanguage();
    if (file.getLanguage() == StdLanguages.HTML || (file.getFileType() == StdFileTypes.JSP && contextLanguage == StdLanguages.HTML)) {
      return StdFileTypes.HTML;
    }
    return StdFileTypes.XML;
  }

  @Override
  public void checkSearchPattern(CompiledPattern pattern) {
    final ValidatingVisitor visitor = new ValidatingVisitor();
    final NodeIterator nodes = pattern.getNodes();
    while (nodes.hasNext()) {
      nodes.current().accept(visitor);
      nodes.advance();
    }
    nodes.reset();
  }

  static class ValidatingVisitor extends PsiRecursiveElementWalkingVisitor {

    @Override
    public void visitErrorElement(PsiErrorElement element) {
      super.visitErrorElement(element);
      final String errorDescription = element.getErrorDescription();
      final PsiElement parent = element.getParent();
      if (parent instanceof XmlAttribute && "'=' expected".equals(errorDescription)) {
        return;
      }
      else if (parent instanceof XmlTag && errorDescription.startsWith("Element") && errorDescription.endsWith(" is not closed")) {
        return;
      }
      throw new MalformedPatternException(errorDescription);
    }
  }

  @Override
  public void checkReplacementPattern(Project project, ReplaceOptions options) {
  }

  @Override
  public StructuralReplaceHandler getReplaceHandler(@NotNull Project project, @NotNull ReplaceOptions replaceOptions) {
    return new XmlReplaceHandler(project, replaceOptions);
  }

  private static class XmlReplaceHandler extends StructuralReplaceHandler {

    @NotNull private final Project myProject;
    @NotNull private final ReplaceOptions myReplaceOptions;

    XmlReplaceHandler(@NotNull Project project, @NotNull ReplaceOptions replaceOptions) {
      myProject = project;
      myReplaceOptions = replaceOptions;
    }

    @Override
    public void replace(ReplacementInfo info, ReplaceOptions options) {
      final PsiElement elementToReplace = StructuralSearchUtil.getPresentableElement(info.getMatch(0));
      assert elementToReplace != null;
      final String replacementToMake = info.getReplacement();
      final PsiElement elementParent = elementToReplace.getParent();
      final boolean listContext = elementParent instanceof XmlTag;

      if (listContext) {
        doReplaceInContext(info, elementToReplace, replacementToMake, elementParent);
      }
      else {
        final PsiElement[] replacements = MatcherImplUtil.createTreeFromText(replacementToMake,
                                                                             PatternTreeContext.Block,
                                                                             myReplaceOptions.getMatchOptions().getFileType(),
                                                                             myProject);
        if (replacements.length > 0) {
          final PsiElement replacement = ReplacerUtil.copySpacesAndCommentsBefore(elementToReplace, replacements, replacementToMake, elementParent);

          // preserve comments
          Replacer.handleComments(elementToReplace, replacement, info);
          elementToReplace.replace(replacement);
        }
        else {
          elementToReplace.delete();
        }
      }
    }

    private void doReplaceInContext(ReplacementInfo info, PsiElement elementToReplace, String replacementToMake, PsiElement elementParent) {
      final PsiElement[] replacements = MatcherImplUtil.createTreeFromText(replacementToMake,
                                                                           PatternTreeContext.Block,
                                                                           myReplaceOptions.getMatchOptions().getFileType(),
                                                                           myProject);

      if (replacements.length > 1) {
        elementParent.addRangeBefore(replacements[0], replacements[replacements.length - 1], elementToReplace);
      }
      else if (replacements.length == 1) {
        Replacer.handleComments(elementToReplace, replacements[0], info);
        try {
          elementParent.addBefore(replacements[0], elementToReplace);
        }
        catch (IncorrectOperationException e) {
          elementToReplace.replace(replacements[0]);
        }
      }

      final int matchSize = info.getMatchesCount();
      for (int i = 0; i < matchSize; ++i) {
        final PsiElement element = StructuralSearchUtil.getPresentableElement(info.getMatch(i));
        final PsiElement prevSibling = element.getPrevSibling();
        element.getParent().deleteChildRange(isWhitespace(prevSibling) ? prevSibling : element, element);
      }
    }

    private static boolean isWhitespace(PsiElement element) {
      if (element instanceof PsiWhiteSpace) return true;
      if (!(element instanceof XmlText)) return false;
      final PsiElement firstChild = element.getFirstChild();
      final PsiElement lastChild = element.getLastChild();
      return firstChild == lastChild && firstChild instanceof PsiWhiteSpace;
    }
  }

  @Override
  public Configuration[] getPredefinedTemplates() {
    return XmlPredefinedConfigurations.createPredefinedTemplates();
  }

  private static class XmlPredefinedConfigurations {
    private static final String HTML_XML = SSRBundle.message("xml_html.category");

    static Configuration[] createPredefinedTemplates() {
      return new Configuration[]{
        createSearchTemplateInfo("xml tag", "<'a/>", HTML_XML, StdFileTypes.XML),
        createSearchTemplateInfo("xml attribute", "<'_tag 'attribute=\"'_value\"/>", HTML_XML, StdFileTypes.XML),
        createSearchTemplateInfo("html attribute", "<'_tag 'attribute />", HTML_XML, StdFileTypes.HTML),
        createSearchTemplateInfo("xml attribute value", "<'_tag '_attribute=\"'value\"/>", HTML_XML, StdFileTypes.XML),
        createSearchTemplateInfo("html attribute value", "<'_tag '_attribute='value />", HTML_XML, StdFileTypes.HTML),
        createSearchTemplateInfo("xml/html tag value", "<table>'_content*</table>", HTML_XML, StdFileTypes.HTML),
        createSearchTemplateInfo("<ul> or <ol>", "<'_tag:[regex( ul|ol )] />", HTML_XML, StdFileTypes.HTML),
        createSearchTemplateInfo("<li> not contained in <ul> or <ol>", "[!within( \"<ul> or <ol>\" )]<li />", HTML_XML, StdFileTypes.HTML)
      };
    }
  }
}
