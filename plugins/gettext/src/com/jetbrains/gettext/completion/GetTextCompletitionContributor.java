package com.jetbrains.gettext.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.jetbrains.gettext.GetTextLanguage;
import com.jetbrains.gettext.GetTextTokenTypes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class GetTextCompletitionContributor extends CompletionContributor {

  private final static String[] KEYWORDS = {
    "msgid",
    "msgstr",
    "msgctxt",
    "msgid_plural",
    "fuzzy",
    "c-format"
  };

  //private final static String[] BUILT_IN_FILTERS = {
  //  "date",
  //  "format",
  //  "replace",
  //  "url_encode",
  //  "json_encode",
  //  "title",
  //  "capitalize",
  //  "upper",
  //  "lower",
  //  "striptags",
  //  "join",
  //  "reverse",
  //  "length",
  //  "sort",
  //  "default",
  //  "keys",
  //  "escape",
  //  "raw",
  //  "merge"
  //};

  private final static List<LookupElementBuilder> KEYWORD_LOOKUPS = new ArrayList<LookupElementBuilder>();
  //private final static List<LookupElementBuilder> BUILT_IN_FILTER_LOOKUPS = new ArrayList<LookupElementBuilder>();

  static {
    for (String keyword : KEYWORDS) {
      KEYWORD_LOOKUPS.add(LookupElementBuilder.create(keyword));
    }
    //for (String filter : BUILT_IN_FILTERS) {
    //  BUILT_IN_FILTER_LOOKUPS.add(LookupElementBuilder.create(filter));
    //}
  }

  public GetTextCompletitionContributor() {
    extend(CompletionType.BASIC, psiElement().withParent(psiElement().withLanguage(GetTextLanguage.INSTANCE)),
           new GetTextKeywordCompletionContributor());
  }

  private static class GetTextKeywordCompletionContributor extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      final PsiElement currElement = parameters.getPosition().getOriginalElement();
      if (currElement.getNode().getElementType() == GetTextTokenTypes.BAD_CHARACTER) {
        for (LookupElementBuilder builder : KEYWORD_LOOKUPS) result.addElement(builder);
        result.stopHere();
        //return;
      }
      //PsiElement prevElement = currElement.getPrevSibling();
      //if (prevElement != null && prevElement instanceof PsiWhiteSpace) prevElement = prevElement.getPrevSibling();
      //if (prevElement != null && prevElement.getNode().getElementType() == TwigTokenTypes.FILTER) {
      //  for (LookupElementBuilder builder : BUILT_IN_FILTER_LOOKUPS) result.addElement(builder);
      //  result.stopHere();
      //}
    }
  }
}
