package com.intellij.grazie.text;

import com.intellij.diagnostic.PluginException;
import com.intellij.grazie.text.TextContent.ExclusionKind;
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
import java.util.function.Function;
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
    new TextContentBuilder(e -> e instanceof OuterLanguageElement ? ExclusionKind.unknown : null, Collections.emptySet(), Collections.emptySet());

  private final Function<PsiElement, @Nullable ExclusionKind> classifier;
  private final Set<Character> indentChars, suffixChars;

  private TextContentBuilder(Function<PsiElement, @Nullable ExclusionKind> classifier,
                             Set<Character> indentChars,
                             Set<Character> suffixChars) {
    this.classifier = classifier;
    this.indentChars = indentChars;
    this.suffixChars = suffixChars;
  }

  /** Exclude and {@link TextContent#markUnknown} all PSI elements satisfying the given condition. */
  public TextContentBuilder withUnknown(Predicate<PsiElement> unknown) {
    return appendClassifier(unknown, ExclusionKind.unknown);
  }

  /** Call {@link TextContent#excludeRange} for all PSI elements satisfying the given condition. */
  public TextContentBuilder excluding(Predicate<PsiElement> excluded) {
    return appendClassifier(excluded, ExclusionKind.exclude);
  }

  /** Exclude with {@link ExclusionKind#markup} all PSI elements satisfying the given condition. */
  public TextContentBuilder withMarkup(Predicate<PsiElement> markup) {
    return appendClassifier(markup, ExclusionKind.markup);
  }

  private TextContentBuilder appendClassifier(Predicate<PsiElement> predicate, ExclusionKind kind) {
    return new TextContentBuilder(e -> {
      ExclusionKind prev = this.classifier.apply(e);
      return prev != null ? prev : predicate.test(e) ? kind : prev;
    }, indentChars, suffixChars);
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
    return new TextContentBuilder(classifier, set, suffixChars);
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
    return new TextContentBuilder(classifier, indentChars, set);
  }

  /**
   * Traverses the whole sub-tree of the {@code root}, applying {@link #withUnknown}/{@link #excluding} rules,
   * {@link #removingIndents}, strips leading and trailing whitespace.
   * If the root has an {@link ElementManipulator}, only its value range is traversed.
   * @return the resulting text content or {@code null} if it's empty.
   */
  public @Nullable TextContent build(PsiElement root, TextContent.TextDomain domain) {
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

  public @Nullable TextContent build(PsiElement root, TextContent.TextDomain domain, TextRange valueRange) {
    TextRange rootRange = root.getTextRange();
    if (isWrongRange(valueRange, rootRange.getLength())) {
      LOG.error("The range " + valueRange + " is out of the PSI element, length " + rootRange.getLength());
      return null;
    }

    int rootStart = rootRange.getStartOffset();
    TextRange fileValueRange = valueRange.shiftRight(rootStart);
    CharSequence fileText = root.getContainingFile().getViewProvider().getContents();
    if (isWrongRange(fileValueRange, fileText.length())) {
      LOG.error("The range " + fileValueRange + " is out of the file, length " + fileText.length());
      return null;
    }

    TextContent content = new PsiRecursiveElementWalkingVisitor() {
      final List<TextContentImpl.TokenInfo> tokens = new ArrayList<>();
      int currentStart = fileValueRange.getStartOffset();

      @Override
      public void visitElement(@NotNull PsiElement element) {
        TextRange range = element.getTextRange().intersection(fileValueRange);
        if (range == null) return;

        @Nullable ExclusionKind kind = classifier.apply(element);
        if (kind == ExclusionKind.exclude) {
          exclusionStarted(range);
        }
        else if (kind != null) {
          exclusionStarted(range);
          var tokenKind = kind == ExclusionKind.markup ? TextContentImpl.TokenKind.markup : TextContentImpl.TokenKind.unknown;
          tokens.add(new TextContentImpl.PsiToken("", root, TextRange.from(range.getStartOffset(), 0).shiftLeft(rootStart), tokenKind));
        }
        else {
          super.visitElement(element);
        }
      }

      private void exclusionStarted(TextRange range) {
        if (range.getStartOffset() != currentStart) {
          TextRange tokenRange = new TextRange(currentStart, range.getStartOffset());
          String tokenText = tokenRange.subSequence(fileText).toString();
          tokens.add(new TextContentImpl.PsiToken(tokenText, root, tokenRange.shiftLeft(rootStart), TextContentImpl.TokenKind.text));
        }
        currentStart = range.getEndOffset();
      }

      @Nullable TextContent walkPsiTree() {
        root.accept(this);
        exclusionStarted(TextRange.from(fileValueRange.getEndOffset(), 0));
        return tokens.isEmpty() ? null : new TextContentImpl(domain, tokens);
      }
    }.walkPsiTree();
    return content == null ? null : content.removeIndents(indentChars).removeLineSuffixes(suffixChars).trimWhitespace();
  }

  private static boolean isWrongRange(TextRange valueRange, int maxLength) {
    return valueRange.getStartOffset() < 0 || valueRange.getEndOffset() > maxLength;
  }
}
