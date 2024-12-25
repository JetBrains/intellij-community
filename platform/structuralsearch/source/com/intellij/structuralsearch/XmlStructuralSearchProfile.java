// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.XmlContextType;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
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
import com.intellij.util.LocalTimeCounter;
import com.intellij.xml.parsing.XmlParserBundle;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.structuralsearch.PredefinedConfigurationUtil.createConfiguration;
import static com.intellij.structuralsearch.PredefinedConfigurationUtil.createLegacyConfiguration;

public class XmlStructuralSearchProfile extends StructuralSearchProfile {

  @Override
  public void compile(PsiElement @NotNull [] elements, @NotNull GlobalCompilingVisitor globalVisitor) {
    new XmlCompilingVisitor(globalVisitor).compile(elements);
  }

  @Override
  public @NotNull PsiElementVisitor createMatchingVisitor(@NotNull GlobalMatchingVisitor globalVisitor) {
    return new XmlMatchingVisitor(globalVisitor);
  }

  @Override
  public boolean isIdentifier(@Nullable PsiElement element) {
    return element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_NAME;
  }

  @Override
  public @NotNull String getTypedVarString(@NotNull PsiElement element) {
    return element instanceof XmlText ? element.getText().trim() : super.getTypedVarString(element);
  }

  @Override
  public boolean isMatchNode(PsiElement element) {
    return !XmlMatchUtil.isWhiteSpace(element) && !(element instanceof PsiErrorElement);
  }

  @Override
  public @NotNull CompiledPattern createCompiledPattern() {
    return new XmlCompiledPattern();
  }

  @Override
  public boolean isMyLanguage(@NotNull Language language) {
    return language instanceof XMLLanguage &&
           (!language.getID().equals("JSP") || ApplicationManager.getApplication().isUnitTestMode());
  }

  @Override
  public PsiElement @NotNull [] createPatternTree(@NonNls @NotNull String text,
                                                  @NotNull PatternTreeContext context,
                                                  @NotNull LanguageFileType fileType,
                                                  @NotNull Language language,
                                                  String contextId,
                                                  @NotNull Project project,
                                                  boolean physical) {
    text = context == PatternTreeContext.File ? text : "<QQQ>" + text + "</QQQ>";
    final @NonNls String fileName = "dummy." + fileType.getDefaultExtension();
    final PsiFile fileFromText =
      PsiFileFactory.getInstance(project).createFileFromText(fileName, fileType, text, LocalTimeCounter.currentTime(), physical, true);

    final XmlDocument document = HtmlUtil.getRealXmlDocument(((XmlFile)fileFromText).getDocument());
    if (context == PatternTreeContext.File) {
      return new PsiElement[] {document};
    }

    assert document != null;
    final XmlTag rootTag = document.getRootTag();
    assert rootTag != null;
    final XmlTagChild[] children = rootTag.getValue().getChildren();
    return (children.length == 1 && children[0] instanceof XmlText) ? children[0].getChildren() : children;
  }

  @Override
  public @NotNull PsiElement extendMatchedByDownUp(@NotNull PsiElement node) {
    if (XmlUtil.isXmlToken(node, XmlTokenType.XML_DATA_CHARACTERS)) {
      final PsiElement parent = node.getParent();
      if (parent.getTextRange().equals(node.getTextRange())) {
        return parent;
      }
    }
    return super.extendMatchedByDownUp(node);
  }

  @Override
  public @NotNull Class<? extends TemplateContextType> getTemplateContextTypeClass() {
    return XmlContextType.class;
  }

  @Override
  public @NotNull LanguageFileType detectFileType(@NotNull PsiElement context) {
    final PsiFile file = context instanceof PsiFile ? (PsiFile)context : context.getContainingFile();
    final Language contextLanguage = context instanceof PsiFile ? null : context.getLanguage();
    if (file.getLanguage() == HTMLLanguage.INSTANCE ||
        (file.getFileType().getName().equals("JSP") && contextLanguage == HTMLLanguage.INSTANCE)) {
      return HtmlFileType.INSTANCE;
    }
    return XmlFileType.INSTANCE;
  }

  @Override
  public void checkSearchPattern(@NotNull CompiledPattern pattern) {
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
    public void visitErrorElement(@NotNull PsiErrorElement element) {
      super.visitErrorElement(element);
      final String errorDescription = element.getErrorDescription();
      final PsiElement parent = element.getParent();
      if (parent instanceof XmlAttribute && XmlParserBundle.message("xml.parsing.expected.attribute.eq.sign").equals(errorDescription)) {
        return;
      }
      else if (parent instanceof XmlTag &&
               XmlParserBundle.message("xml.parsing.named.element.is.not.closed", ((XmlTag)parent).getName()).equals(errorDescription)) {
        return;
      }
      throw new MalformedPatternException(errorDescription);
    }
  }

  @Override
  public void checkReplacementPattern(@NotNull Project project, @NotNull ReplaceOptions options) {
  }

  @Override
  public StructuralReplaceHandler getReplaceHandler(@NotNull Project project, @NotNull ReplaceOptions replaceOptions) {
    return new XmlReplaceHandler(project, replaceOptions);
  }

  private static class XmlReplaceHandler extends StructuralReplaceHandler {

    private final @NotNull Project myProject;
    private final @NotNull ReplaceOptions myReplaceOptions;

    XmlReplaceHandler(@NotNull Project project, @NotNull ReplaceOptions replaceOptions) {
      myProject = project;
      myReplaceOptions = replaceOptions;
    }

    @Override
    public void replace(@NotNull ReplacementInfo info, @NotNull ReplaceOptions options) {
      final PsiElement elementToReplace = StructuralSearchUtil.getPresentableElement(info.getMatch(0));
      assert elementToReplace != null;
      final String replacementToMake = info.getReplacement();
      final PsiElement elementParent = elementToReplace.getParent();
      final boolean listContext = elementParent instanceof XmlTag;

      if (listContext) {
        doReplaceInContext(info, elementToReplace, replacementToMake, elementParent);
      }
      else {
        final LanguageFileType fileType = myReplaceOptions.getMatchOptions().getFileType();
        assert fileType != null;
        final PsiElement[] replacements = MatcherImplUtil.createTreeFromText(replacementToMake,
                                                                             PatternTreeContext.Block,
                                                                             fileType,
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
      final LanguageFileType fileType = myReplaceOptions.getMatchOptions().getFileType();
      assert fileType != null;
      PsiElement[] replacements = MatcherImplUtil.createTreeFromText(replacementToMake,
                                                                     PatternTreeContext.Block,
                                                                     fileType,
                                                                     myProject);
      if (replacements.length > 0 && !(replacements[0] instanceof XmlAttribute) && !(replacements[0] instanceof XmlTagChild)) {
        replacements = new PsiElement[] { replacements[0].getParent() };
      }

      if (replacements.length > 1) {
        elementParent.addRangeBefore(replacements[0], replacements[replacements.length - 1], elementToReplace);
      }
      else if (replacements.length == 1) {
        elementToReplace.replace(replacements[0]);
        return;
      }

      final int matchSize = info.getMatchesCount();
      for (int i = 0; i < matchSize; ++i) {
        final PsiElement match = info.getMatch(i);
        if (match == null) continue;
        final PsiElement element = StructuralSearchUtil.getPresentableElement(match);
        final PsiElement prevSibling = element.getPrevSibling();
        element.getParent().deleteChildRange(XmlMatchUtil.isWhiteSpace(prevSibling) ? prevSibling : element, element);
      }
    }
  }

  @Override
  public Configuration @NotNull [] getPredefinedTemplates() {
    return XmlPredefinedConfigurations.createPredefinedTemplates();
  }

  private static final class XmlPredefinedConfigurations {
    static Configuration[] createPredefinedTemplates() {
      return new Configuration[]{
        createLegacyConfiguration(SSRBundle.message("predefined.template.xml.tag"), "Xml tag",
                                  "<'a/>", getHtmlXml(), XmlFileType.INSTANCE),
        createLegacyConfiguration(SSRBundle.message("predefined.template.xml.attribute"), "Xml attribute",
                                  "<'_tag 'attribute=\"'_value\"/>", getHtmlXml(), XmlFileType.INSTANCE),
        createLegacyConfiguration(SSRBundle.message("predefined.template.html.attribute"), "Html attribute",
                                  "<'_tag 'attribute />", getHtmlXml(), HtmlFileType.INSTANCE),
        createLegacyConfiguration(SSRBundle.message("predefined.template.xml.attribute.value"), "Xml attribute value",
                                  "<'_tag '_attribute=\"'value\"/>", getHtmlXml(), XmlFileType.INSTANCE),
        createLegacyConfiguration(SSRBundle.message("predefined.template.html.attribute.value"), "Html attribute value",
                                  "<'_tag '_attribute='value />", getHtmlXml(), HtmlFileType.INSTANCE),
        createLegacyConfiguration(SSRBundle.message("predefined.template.xml.html.tag.value"), "Xml/html tag value",
                                  "<table>'_content*</table>", getHtmlXml(), HtmlFileType.INSTANCE),
        createLegacyConfiguration(SSRBundle.message("predefined.template.ul.or.ol"), "<ul> or <ol>",
                                  "<'_tag:[regex( ul|ol )] />", getHtmlXml(), HtmlFileType.INSTANCE),
        createLegacyConfiguration(SSRBundle.message("predefined.template.li.not.contained.in.ul.or.ol"), "<li> not contained in <ul> or <ol>",
                                  "[!within( <ul> or <ol> )]<li />", getHtmlXml(), HtmlFileType.INSTANCE),
        createLegacyConfiguration(SSRBundle.message("predefined.configuration.xml.attribute.referencing.java.class"), "xml attribute referencing java class",
                                  "<'_tag 'attribute=\"'_value:[ref( classes, interfaces \\& enums )]\"/>", getHtmlXml(), XmlFileType.INSTANCE),
        createConfiguration(SSRBundle.message("predefined.template.xml.tag.without.specific.attribute"), "XML tag without a specific attribute",
                            "<'_tag '_attr{0,0}:attributeName />", getHtmlXml(), XmlFileType.INSTANCE),
      };
    }

    private static String getHtmlXml() {
      return SSRBundle.message("xml_html.category");
    }
  }
}
