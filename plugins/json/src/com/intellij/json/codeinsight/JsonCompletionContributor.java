package com.intellij.json.codeinsight;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonProperty;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author Mikhail Golubev
 */
public class JsonCompletionContributor extends CompletionContributor {
  private static final Logger LOG = Logger.getInstance(JsonCompletionContributor.class);
  private static final String COMPLETION_PLACEHOLDER = "\"" + CompletionInitializationContext.DUMMY_IDENTIFIER + "\"";

  private static final PsiElementPattern.Capture<PsiElement> AFTER_COLON_IN_PROPERTY = psiElement()
    .afterLeaf(":").withSuperParent(2, JsonProperty.class);
  private static final PsiElementPattern.Capture<PsiElement> AFTER_COMMA_OR_BRACKET_IN_ARRAY = psiElement()
    .afterLeaf("[", ",").withSuperParent(2, JsonArray.class);
  @Override
  public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
    LOG.debug(DebugUtil.psiToString(parameters.getPosition().getContainingFile(), true));
    super.fillCompletionVariants(parameters, result);
  }


  public JsonCompletionContributor() {
    extend(CompletionType.BASIC, AFTER_COLON_IN_PROPERTY, MyKeywordsCompletionProvider.INSTANCE);
    extend(CompletionType.BASIC, AFTER_COMMA_OR_BRACKET_IN_ARRAY, MyKeywordsCompletionProvider.INSTANCE);
  }


  @Override
  public void beforeCompletion(@NotNull CompletionInitializationContext context) {
    context.setDummyIdentifier(COMPLETION_PLACEHOLDER);
  }

  private static class MyKeywordsCompletionProvider extends CompletionProvider<CompletionParameters> {
    private static final MyKeywordsCompletionProvider INSTANCE = new MyKeywordsCompletionProvider();
    private static final String[] KEYWORDS = new String[]{"null", "true", "false"};

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      for (String keyword : KEYWORDS) {
        result.addElement(LookupElementBuilder.create(keyword).bold());
      }
    }
  }
}
