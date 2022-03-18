package com.intellij.grazie.text;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

/**
 * A class encapsulating common utilities needed for extracting {@link TextContent} from PSI.
 * The common usage pattern is {@code TextContentBuilder.FromPsi. ... .build(psi, domain)}.
 */
public class TextContentBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.grazie.text.TextContentBuilder");

  /**
   * A basic builder that takes the full PSI text, considering any {@link OuterLanguageElement}s as unknown fragments.
   */
  public static final TextContentBuilder FromPsi =
    new TextContentBuilder(e -> e instanceof OuterLanguageElement, e -> false, Collections.emptySet(), Collections.emptySet());

  private final Predicate<PsiElement> unknown;
  private final Predicate<PsiElement> excluded;
  private final Set<Character> indentChars, suffixChars;

  private TextContentBuilder(Predicate<PsiElement> unknown,
                             Predicate<PsiElement> excluded,
                             Set<Character> indentChars,
                             Set<Character> suffixChars) {
    this.unknown = unknown;
    this.excluded = excluded;
    this.indentChars = indentChars;
    this.suffixChars = suffixChars;
  }

  /** Exclude and {@link TextContent#markUnknown} all PSI elements satisfying the given condition. */
  public TextContentBuilder withUnknown(Predicate<PsiElement> unknown) {
    return new TextContentBuilder(e -> this.unknown.test(e) || unknown.test(e), excluded, indentChars, suffixChars);
  }

  /** Call {@link TextContent#excludeRange} for all PSI elements satisfying the given condition. */
  public TextContentBuilder excluding(Predicate<PsiElement> excluded) {
    return new TextContentBuilder(unknown, e -> this.excluded.test(e) || excluded.test(e), indentChars, suffixChars);
  }

  /**
   * For each line of the text, remove the prefix consisting of the given characters
   * (packed together in a string for compactness)
   */
  public TextContentBuilder removingIndents(String indentChars) {
    Set<Character> set = new HashSet<>(this.indentChars);
    for (int i = 0; i < indentChars.length(); i++) {
      set.add(indentChars.charAt(i));
    }
    return new TextContentBuilder(unknown, excluded, set, suffixChars);
  }

  /**
   * For each line of the text, remove the suffix consisting of the given characters
   * (packed together in a string for compactness)
   */
  public TextContentBuilder removingLineSuffixes(String suffixChars) {
    Set<Character> set = new HashSet<>(this.suffixChars);
    for (int i = 0; i < suffixChars.length(); i++) {
      set.add(suffixChars.charAt(i));
    }
    return new TextContentBuilder(unknown, excluded, indentChars, set);
  }

  /**
   * Traverses the whole sub-tree of the {@code root}, applying {@link #withUnknown}/{@link #excluding} rules,
   * {@link #removingIndents}, strips leading and trailing whitespace.
   * If the root has an {@link ElementManipulator}, only its value range is traversed.
   * @return the resulting text content or {@code null} if it's empty.
   */
  @Nullable
  public TextContent build(PsiElement root, TextContent.TextDomain domain) {
    ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(root);
    int length = root.getTextLength();
    if (manipulator != null) {
      TextRange valueRange = manipulator.getRangeInElement(root);
      if (isWrongRange(valueRange, length)) {
        PluginException.logPluginError(LOG,
                                       "The manipulator returned an incorrect range " + valueRange +
                                       " for PSI " + root.getClass() +
                                       " of length " + length,
                                       null, manipulator.getClass());
        return null;
      }
      return build(root, domain, valueRange);
    }
    return build(root, domain, new TextRange(0, length));
  }

  @Nullable
  public TextContent build(PsiElement root, TextContent.TextDomain domain, TextRange valueRange) {
    int rootStart = root.getTextRange().getStartOffset();
    String rootText = root.getText();
    if (isWrongRange(valueRange, rootText.length())) {
      LOG.error("The range " + valueRange + " is out of the PSI element, length " + rootText.length());
      return null;
    }

    TextContent content = new PsiRecursiveElementWalkingVisitor() {
      final List<TextContentImpl.TokenInfo> tokens = new ArrayList<>();
      int currentStart = valueRange.getStartOffset();

      @Override
      public void visitElement(@NotNull PsiElement element) {
        TextRange range = element.getTextRange().shiftLeft(rootStart).intersection(valueRange);
        if (range == null) return;

        if (unknown.test(element)) {
          exclusionStarted(range);
          tokens.add(new TextContentImpl.PsiToken("", root, TextRange.from(range.getStartOffset(), 0), true));
        }
        else if (excluded.test(element)) {
          exclusionStarted(range);
        }
        else {
          super.visitElement(element);
        }
      }

      private void exclusionStarted(TextRange range) {
        if (range.getStartOffset() != currentStart) {
          TextRange tokenRange = new TextRange(currentStart, range.getStartOffset());
          tokens.add(new TextContentImpl.PsiToken(tokenRange.substring(rootText), root, tokenRange, false));
        }
        currentStart = range.getEndOffset();
      }

      @Nullable TextContent walkPsiTree() {
        root.accept(this);
        exclusionStarted(TextRange.from(valueRange.getEndOffset(), 0));
        return tokens.isEmpty() ? null : new TextContentImpl(domain, tokens);
      }
    }.walkPsiTree();
    return content == null ? null : content.removeIndents(indentChars).removeLineSuffixes(suffixChars).trimWhitespace();
  }

  private static boolean isWrongRange(TextRange valueRange, int maxLength) {
    return valueRange.getStartOffset() < 0 || valueRange.getEndOffset() > maxLength;
  }
}
