package com.intellij.lang.properties;

import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.Commenter;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.psi.PsiElement;
import com.intellij.lang.properties.structureView.PropertiesStructureViewModel;
import com.intellij.lang.properties.findUsages.PropertiesFindUsagesProvider;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 27, 2005
 * Time: 6:03:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesLanguage extends Language {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.javascript.JavascriptLanguage");
  //private static final JSAnnotatingVisitor ANNOTATOR = new JSAnnotatingVisitor();

  public PropertiesLanguage() {
    super("Properties");
  }

  public ParserDefinition getParserDefinition() {
    return new PropertiesParserDefinition();
  }

  public SyntaxHighlighter getSyntaxHighlighter(Project project) {
    return new PropertiesHighlighter();
  }


  public WordsScanner getWordsScanner() {
    return new PropertiesWordsScanner();
  }

  //public boolean mayHaveReferences(IElementType token, final short searchContext) {
  //  if ((searchContext & UsageSearchContext.IN_CODE) != 0 && token == JSElementTypes.REFERENCE_EXPRESSION) return true;
  //  if ((searchContext & UsageSearchContext.IN_COMMENTS) != 0 && getParserDefinition().getCommentTokens().isInSet(token)) return true;
  //  if ((searchContext & UsageSearchContext.IN_STRINGS) != 0 && token == JSTokenTypes.STRING_LITERAL) return true;
  //  return false;
  //}

  //public FoldingBuilder getFoldingBuilder() {
  //  return new JavaScriptFoldingBuilder();
  //}
  //
  //public PseudoTextBuilder getFormatter() {
  //  return new JavaScriptPseudoTextBuilder();
  //}
  //
  //public PairedBraceMatcher getPairedBraceMatcher() {
  //  return new JSBraceMatcher();
  //}

  //public Annotator getAnnotator() {
  //  return ANNOTATOR;
  //}
  //
  public StructureViewBuilder getStructureViewBuilder(final PsiElement psiElement) {
    return new TreeBasedStructureViewBuilder() {
      public StructureViewModel createStructureViewModel() {
        return new PropertiesStructureViewModel(psiElement);
      }
    };
  }

  public FindUsagesProvider getFindUsagesProvider() {
    return new PropertiesFindUsagesProvider();
  }

  public Commenter getCommenter() {
    return new PropertiesCommenter();
  }
}
