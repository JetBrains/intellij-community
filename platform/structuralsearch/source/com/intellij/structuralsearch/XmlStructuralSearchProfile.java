/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.structuralsearch;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.XmlContextType;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.structuralsearch.impl.matcher.*;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.compiler.XmlCompilingVisitor;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementContext;
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
                                        @NotNull FileType fileType,
                                        @Nullable Language language,
                                        String contextName, @Nullable String extension,
                                        @NotNull Project project,
                                        boolean physical) {
    final String ext = extension != null ? extension : fileType.getDefaultExtension();
    String text1 = context == PatternTreeContext.File ? text : "<QQQ>" + text + "</QQQ>";
    final PsiFile fileFromText = PsiFileFactory.getInstance(project)
      .createFileFromText("dummy." + ext, fileType, text1, LocalTimeCounter.currentTime(), physical, true);

    final XmlDocument document = HtmlUtil.getRealXmlDocument(((XmlFile)fileFromText).getDocument());
    if (context == PatternTreeContext.File) {
      return new PsiElement[]{document};
    }

    return document.getRootTag().getValue().getChildren();
  }

  @Override
  public Class<? extends TemplateContextType> getTemplateContextTypeClass() {
    return XmlContextType.class;
  }

  @NotNull
  @Override
  public FileType detectFileType(@NotNull PsiElement context) {
    PsiFile file = context instanceof PsiFile ? (PsiFile)context : context.getContainingFile();
    Language contextLanguage = context instanceof PsiFile ? null : context.getLanguage();
    if (file.getLanguage() == StdLanguages.HTML || (file.getFileType() == StdFileTypes.JSP && contextLanguage == StdLanguages.HTML)) {
      return StdFileTypes.HTML;
    }
    return StdFileTypes.XML;
  }

  @Override
  public void checkReplacementPattern(Project project, ReplaceOptions options) {
  }

  @Override
  public StructuralReplaceHandler getReplaceHandler(@NotNull ReplacementContext context) {
    return new MyReplaceHandler(context);
  }

  private static class MyReplaceHandler extends StructuralReplaceHandler {
    private final ReplacementContext myContext;

    private MyReplaceHandler(ReplacementContext context) {
      myContext = context;
    }

    public void replace(ReplacementInfo info, ReplaceOptions options) {
      PsiElement elementToReplace = info.getMatch(0);
      assert elementToReplace != null;
      PsiElement elementParent = elementToReplace.getParent();
      String replacementToMake = info.getReplacement();
      boolean listContext = elementToReplace.getParent() instanceof XmlTag;

      if (listContext) {
        doReplaceInContext(info, elementToReplace, replacementToMake, elementParent, myContext);
      }

      PsiElement[] statements = ReplacerUtil.createTreeForReplacement(replacementToMake, PatternTreeContext.Block, myContext);

      if (statements.length > 0) {
        PsiElement replacement = ReplacerUtil.copySpacesAndCommentsBefore(elementToReplace, statements, replacementToMake, elementParent);

        // preserve comments
        Replacer.handleComments(elementToReplace, replacement, myContext);
        elementToReplace.replace(replacement);
      }
      else {
        elementToReplace.delete();
      }
    }
  }

  private static void doReplaceInContext(ReplacementInfo info,
                                         PsiElement elementToReplace,
                                         String replacementToMake,
                                         PsiElement elementParent,
                                         ReplacementContext context) {
    PsiElement[] statements = ReplacerUtil.createTreeForReplacement(replacementToMake, PatternTreeContext.Block, context);

    if (statements.length > 1) {
      elementParent.addRangeBefore(statements[0], statements[statements.length - 1], elementToReplace);
    }
    else if (statements.length == 1) {
      PsiElement replacement = statements[0];

      Replacer.handleComments(elementToReplace, replacement, context);

      try {
        elementParent.addBefore(replacement, elementToReplace);
      }
      catch (IncorrectOperationException e) {
        elementToReplace.replace(replacement);
      }
    }

    final int matchSize = info.getMatchesCount();

    for (int i = 0; i < matchSize; ++i) {
      PsiElement element = info.getMatch(i);

      if (element == null) continue;
      PsiElement firstToDelete = element;
      PsiElement lastToDelete = element;
      PsiElement prevSibling = element.getPrevSibling();
      PsiElement nextSibling = element.getNextSibling();

      if (prevSibling instanceof PsiWhiteSpace) {
        firstToDelete = prevSibling;
      }
      else if (prevSibling == null && nextSibling instanceof PsiWhiteSpace) {
        lastToDelete = nextSibling;
      }
      if (nextSibling instanceof XmlText && i + 1 < matchSize) {
        final PsiElement next = info.getMatch(i + 1);
        if (next != null && next == nextSibling.getNextSibling()) {
          lastToDelete = nextSibling;
        }
      }
      element.getParent().deleteChildRange(firstToDelete, lastToDelete);
    }
  }

  @Override
  public Configuration[] getPredefinedTemplates() {
    return XmlPredefinedConfigurations.createPredefinedTemplates();
  }

  private static class XmlPredefinedConfigurations {
    private static final String HTML_XML = SSRBundle.message("xml_html.category");

    private static Configuration[] createPredefinedTemplates() {
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
