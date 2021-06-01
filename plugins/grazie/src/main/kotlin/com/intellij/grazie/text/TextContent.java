package com.intellij.grazie.text;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Natural language content extracted from one or more PSI elements and concatenated.
 * This object is immutable (as long as the underlying PSI stays intact).
 */
@ApiStatus.NonExtendable
public interface TextContent extends CharSequence {

  /** The domain of all underlying PSI elements */
  TextDomain getDomain();

  /** The concatenated extracted natural language text without PSI markup */
  @Override
  String toString();

  /**
   * Translate an offset in this text into a corresponding offset in the PSI.
   * In case of ambiguities, a heuristic is applied, preferring PSI offsets near non-whitespace characters.
   */
  int textOffsetToFile(int textOffset);

  /**
   * Translate an offset in this text into a corresponding offset in the PSI.
   * Ambiguities are resolved according to {@code leanForward}:
   * if {@code true}, the last possible PSI offset is returned, otherwise the first one.
   */
  int textOffsetToFile(int textOffset, boolean leanForward);

  /**
   * Translate an offset in the underlying PSI into a corresponding offset in the natural language text.
   * @return a natural language text offset corresponding to the given offset in the PSI file,
   * or {@code null} if this content doesn't cover the given file offset.
   */
  @Nullable Integer fileOffsetToText(int fileOffset);

  /**
   * @return the range with start and end translated using {@link #fileOffsetToText}.
   */
  @Nullable TextRange fileRangeToText(TextRange fileRange);

  /**
   * @return the PSI element containing all of this content's fragments.
   */
  @NotNull PsiElement getCommonParent();

  /**
   * @return the leaf PSI element containing the given text offset.
   */
  @NotNull PsiElement findPsiElementAt(int textOffset);

  /**
   * @return the list of all non-empty ranges in the containing file which this text content covers,
   * in the order of their appearance in the text content.
   */
  @NotNull List<TextRange> getRangesInFile();

  /**
   * @return whether the given extracted text range contains any fragments previously excluded by {@link #markUnknown}.
   */
  boolean hasUnknownFragmentsIn(TextRange rangeInText);

  /**
   * @return a copy of this TextContent with the given range excluded.
   * Note: the range is taken contain natural language text offsets, not PSI ones.
   * So if you need to exclude several ranges, you should shift them after preceding ones are processed,
   * or exclude them in last-to-first order.
   */
  @Contract(pure = true)
  TextContent excludeRange(TextRange rangeInText);

  /**
   * The same as {@link #excludeRange} with additional semantics that the range isn't just removed silently,
   * but it's assumed that it's replaced with some unknown content,
   * so the text around that range will probably be malformed, and error reporting there might need suppressing.
   */
  @Contract(pure = true)
  TextContent markUnknown(TextRange rangeInText);

  /**
   * @return whether the given PSI file text range has non-empty intersection with any fragment covered by this text content.
   */
  boolean intersectsRange(TextRange rangeInFile);

  /**
   * @return a copy of this text content with all leading and trailing whitespace characters removed
   * (as in {@link Character#isWhitespace(int)} and {@link Character#isSpaceChar(char)}),
   * or {@code null} if the text consists only of whitespace.
   */
  @Nullable TextContent trimWhitespace();

  enum TextDomain {
    /** String literals of a programming language */
    LITERALS,

    /** Generic comments of a programming language, excluding doc comments */
    COMMENTS,

    /** In-code documentation (JavaDocs, Python DocStrings, etc.) */
    DOCUMENTATION,

    /** Plain text (e.g. txt, Markdown, HTML, LaTeX) and UI strings */
    PLAIN_TEXT;

    /** An unmodifiable set of all {@link TextDomain} enum constants */
    public static final Set<TextDomain> ALL = Collections.unmodifiableSet(EnumSet.allOf(TextDomain.class));
  }

  /**
   * @return a new TextContent in the given domain, containing the substring of the given {@code psi.getText()}
   * in the specified range.
   */
  static TextContent psiFragment(TextDomain domain, PsiElement psi, TextRange rangeInPsi) {
    return new TextContentImpl(domain, Collections.singletonList(
      new TextContentImpl.PsiToken(rangeInPsi.substring(psi.getText()), psi, rangeInPsi, false)));
  }

  /**
   * @return a new TextContent in the given domain, containing the full {@code psi.getText()}.
   */
  static TextContent psiFragment(TextDomain domain, PsiElement psi) {
    return psiFragment(domain, psi, TextRange.from(0, psi.getTextLength()));
  }

  /**
   * @return a concatenation of several text contents, which must have the same domains.
   */
  static TextContent join(List<? extends TextContent> components) {
    if (components.size() == 1) return components.get(0);

    return new TextContentImpl(commonDomain(components), ContainerUtil.flatMap(components, c -> ((TextContentImpl) c).tokens));
  }

  /**
   * @return a concatenation of several text contents (which must have the same domains)
   * with a single synthetic space character inserted between each pair of adjacent components.
   */
  @Nullable
  static TextContent joinWithWhitespace(List<TextContent> components) {
    if (components.isEmpty()) return null;
    if (components.size() == 1) return components.get(0);

    return new TextContentImpl(commonDomain(components),
      StreamEx.of(components)
        .map(c -> ((TextContentImpl) c).tokens)
        .intersperse(Collections.singletonList(TextContentImpl.WS_TOKEN))
        .toFlatList(Function.identity()));
  }

  private static TextDomain commonDomain(List<? extends TextContent> components) {
    TextDomain domain = components.get(0).getDomain();
    if (components.stream().anyMatch(c -> c.getDomain() != domain)) {
      throw new IllegalArgumentException("Joined TextContents should share the same domain");
    }
    return domain;
  }
}
