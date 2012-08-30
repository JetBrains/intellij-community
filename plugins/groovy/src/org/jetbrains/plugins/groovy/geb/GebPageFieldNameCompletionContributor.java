package org.jetbrains.plugins.groovy.geb;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.util.FieldInitializerTailTypes;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiClass;
import static com.intellij.patterns.PsiJavaPatterns.psiField;

/**
 * @author Sergey Evdokimov
 */
public class GebPageFieldNameCompletionContributor extends CompletionContributor {

  public GebPageFieldNameCompletionContributor() {
    extend(CompletionType.BASIC, psiElement(GroovyTokenTypes.mIDENT).withParent(
      psiField().withModifiers(PsiModifier.STATIC).inClass(psiClass().inheritorOf(true, "geb.Page"))), new GebCompletionProvider());
  }

  private static class GebCompletionProvider extends CompletionProvider<CompletionParameters> {

    @SuppressWarnings("unchecked")
    private static Pair<String, TailType>[] VARIANTS = new Pair[]{
      Pair.create("url", FieldInitializerTailTypes.EQ_STRING),
      Pair.create("content", FieldInitializerTailTypes.EQ_CLOSURE),
      Pair.create("at", FieldInitializerTailTypes.EQ_CLOSURE),
    };

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      final PsiClass psiClass = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiClass.class);
      assert psiClass != null;

      for (Pair<String, TailType> trinity : VARIANTS) {
        String fieldName = trinity.first;

        if (psiClass.findFieldByName(fieldName, false) == null) {
          result.addElement(TailTypeDecorator.withTail(LookupElementBuilder.create(fieldName), trinity.second));
        }
      }
    }
  }
}
