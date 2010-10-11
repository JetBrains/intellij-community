package org.jetbrains.javafx.codeInsight.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.JavaFxLanguage;
import org.jetbrains.javafx.lang.psi.JavaFxBlockExpression;
import org.jetbrains.javafx.lang.psi.JavaFxClassDefinition;
import org.jetbrains.javafx.lang.psi.JavaFxFile;
import org.jetbrains.javafx.lang.psi.JavaFxLoopExpression;

import java.util.Collection;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxKeywordCompletionContributor extends CompletionContributor {
  private static final PsiElementPattern.Capture<PsiElement> JAVA_FX_ELEMENT_CAPTURE =
    psiElement().withLanguage(JavaFxLanguage.getInstance());

  private static final Set<String> COMMON_MODIFIERS = new HashSet<String>();
  private static final Set<String> SCRIPT_ITEMS = new HashSet<String>();
  private static final Set<String> EXPRESSIONS = new HashSet<String>();
  private static final Set<String> VALUE_EXPRESSIONS = new HashSet<String>();
  private static final Set<String> INITIALIZER_PARTS = new HashSet<String>();

  static {
    COMMON_MODIFIERS.add("abstract");
    COMMON_MODIFIERS.add("bound");
    COMMON_MODIFIERS.add("public");
    COMMON_MODIFIERS.add("public-read");
    COMMON_MODIFIERS.add("protected");  // ?
    COMMON_MODIFIERS.add("package");

    SCRIPT_ITEMS.add("import");
    SCRIPT_ITEMS.add("function");
    SCRIPT_ITEMS.add("class");

    EXPRESSIONS.add("insert");
    EXPRESSIONS.add("delete");
    EXPRESSIONS.add("while");
    EXPRESSIONS.add("throw");
    EXPRESSIONS.add("try");

    VALUE_EXPRESSIONS.add("if");
    VALUE_EXPRESSIONS.add("for");
    VALUE_EXPRESSIONS.add("new");
    VALUE_EXPRESSIONS.add("def");
    VALUE_EXPRESSIONS.add("var");
    VALUE_EXPRESSIONS.add("insert");
    VALUE_EXPRESSIONS.add("delete");
    VALUE_EXPRESSIONS.add("reverse");
    VALUE_EXPRESSIONS.add("return");

    INITIALIZER_PARTS.add("bind");
    INITIALIZER_PARTS.add("false");
    INITIALIZER_PARTS.add("function");
    INITIALIZER_PARTS.add("new");
    INITIALIZER_PARTS.add("null");
    INITIALIZER_PARTS.add("true");
  }

  private static final PsiElementPattern.Capture<PsiElement> AT_TOP_LEVEL =
    JAVA_FX_ELEMENT_CAPTURE.withSuperParent(2, JavaFxFile.class).andNot(psiElement().inside(JavaFxClassDefinition.class));
  private static final PsiElementPattern.Capture<PsiElement> IN_BLOCK =
    JAVA_FX_ELEMENT_CAPTURE.withSuperParent(2, JavaFxBlockExpression.class);
  private static final PsiElementPattern.Capture<PsiElement> IN_LOOP = JAVA_FX_ELEMENT_CAPTURE.inside(JavaFxLoopExpression.class);
  private static final PsiElementPattern.Capture<PsiElement> IN_CLASS = JAVA_FX_ELEMENT_CAPTURE.withParent(JavaFxClassDefinition.class);
  private static final PsiElementPattern.Capture<PsiElement> AFTER_EQ = JAVA_FX_ELEMENT_CAPTURE.afterLeaf("=");

  public JavaFxKeywordCompletionContributor() {
    topLevel();
    inBlock();
    inLoop();
    inClass();
    afterEq();
  }

  private void topLevel() {
    final Set<String> keywords = new HashSet<String>(COMMON_MODIFIERS);
    keywords.addAll(SCRIPT_ITEMS);
    keywords.addAll(EXPRESSIONS);
    keywords.addAll(VALUE_EXPRESSIONS);
    keywords.add("mixin");
    extend(CompletionType.BASIC, psiElement().and(AT_TOP_LEVEL), provider(keywords));
  }

  private void inBlock() {
    final Set<String> keywords = new HashSet<String>(EXPRESSIONS);
    keywords.addAll(VALUE_EXPRESSIONS);
    extend(CompletionType.BASIC, psiElement().and(IN_BLOCK), provider(keywords));
  }

  private void inLoop() {
    extend(CompletionType.BASIC, psiElement().and(IN_LOOP), provider("break", "continue"));
  }

  private void inClass() {
    final Set<String> keywords = new HashSet<String>(COMMON_MODIFIERS);
    keywords.add("public-init");
    keywords.add("override");
    keywords.add("def");
    keywords.add("var");
    keywords.add("function");
    keywords.add("init");
    keywords.add("post-init");
    extend(CompletionType.BASIC, psiElement().and(IN_CLASS), provider(keywords));
  }

  private void afterEq() {
    extend(CompletionType.BASIC, psiElement().and(AFTER_EQ), provider(INITIALIZER_PARTS));
  }

  private static CompletionProvider<CompletionParameters> provider(@NotNull final Collection<String> keywords) {
    return provider(ArrayUtil.toStringArray(keywords));
  }

  private static CompletionProvider<CompletionParameters> provider(@NotNull final String... keywords) {
    return new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull final CompletionParameters parameters,
                                    final ProcessingContext context,
                                    @NotNull final CompletionResultSet result) {
        for (String keyword : keywords) {
          result.addElement(LookupElementBuilder.create(keyword).setBold());
        }
      }
    };
  }
}
