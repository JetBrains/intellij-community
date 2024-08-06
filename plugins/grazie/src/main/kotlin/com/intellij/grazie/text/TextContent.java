package com.intellij.grazie.text;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
public interface TextContent extends CharSequence, UserDataHolderEx {

  /** The domain of all underlying PSI elements */
  TextDomain getDomain();

  /** The concatenated extracted natural language text without PSI markup */
  @Override
  String toString();

  /**
   * Translate an offset in this text into a corresponding offset in the PSI.
   * In the case of ambiguities, a heuristic is applied, preferring PSI offsets near non-whitespace characters.
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
   * @return the range with start and end translated using {@link #textOffsetToFile(int)}.
   */
  default @NotNull TextRange textRangeToFile(TextRange textRange) {
    return new TextRange(textOffsetToFile(textRange.getStartOffset()), textOffsetToFile(textRange.getEndOffset()));
  }

  /**
   * @return the PSI element containing all of this content's fragments.
   */
  @NotNull PsiElement getCommonParent();

  /**
   * @return the PSI file containing this content.
   */
  @NotNull PsiFile getContainingFile();

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
   * Note: the range contains natural language text offsets, not PSI ones.
   * So if you need to exclude several ranges, you should shift them after preceding ones are processed,
   * or exclude them in last-to-first order.
   */
  @Contract(pure = true)
  TextContent excludeRange(TextRange rangeInText);

  /**
   * The same as {@link #excludeRange} with additional semantics that the range isn't just removed silently,
   * but it's assumed that it's replaced with some unknown content.
   * So the text around that range will probably be malformed, and error reporting there might need suppressing.
   */
  @Contract(pure = true)
  TextContent markUnknown(TextRange rangeInText);

  /**
   * @return a copy of this TextContent with the given ranges excluded.
   * This is equivalent to calling {@link #excludeRange} or {@link #markUnknown} for the ranges in reversed order, but works faster.
   * Note: the ranges contain natural language text offsets, not PSI ones.
   * They should not overlap and should be sorted.
   */
  @Contract(pure = true)
  TextContent excludeRanges(List<Exclusion> ranges);

  /**
   * @return the part of this text inside the corresponding range, or {@code null} if the result is empty
   */
  default @Nullable TextContent subText(TextRange range) {
    if (range.isEmpty()) return null;
    return excludeRange(new TextRange(range.getEndOffset(), length())).excludeRange(new TextRange(0, range.getStartOffset()));
  }

  /**
   * @return whether the given PSI file text range has non-empty intersection with any fragment covered by this text content.
   */
  @Contract(pure = true)
  boolean intersectsRange(TextRange rangeInFile);

  /**
   * @return all sub-ranges of {@code rangeInFile} which this text maps into; the same as the intersections of {@code rangeInFile} and {@link #getRangesInFile()}.
   * Empty ranges are also included.
   */
  @Contract(pure = true)
  default List<TextRange> intersection(TextRange rangeInFile) {
    return ContainerUtil.mapNotNull(getRangesInFile(), r -> r.intersection(rangeInFile));
  }

  /**
   * @return a copy of this text content with all leading and trailing whitespace characters removed
   * (as in {@link Character#isWhitespace(int)} and {@link Character#isSpaceChar(char)}),
   * or {@code null} if the text consists only of whitespace.
   */
  @Contract(pure = true)
  @Nullable TextContent trimWhitespace();

  /** For each line of the text, remove the prefix consisting of the given characters. */
  @Contract(pure = true)
  TextContent removeIndents(Set<Character> indentChars);

  /** For each line of the text, remove the suffix consisting of the given characters. */
  @Contract(pure = true)
  TextContent removeLineSuffixes(Set<Character> suffixChars);

  /** Offsets in this text where exclusions of type {@link ExclusionKind#markup} were applied */
  int[] markupOffsets();

  /** Return a copy of this text with the specified character inserted into {@link #markupOffsets()} */
  WithMarkup replaceMarkupWith(char c);

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
   * Consider using {@link #builder()} instead if there's any chance that your language might be used as a data language in templates
   * (see e.g. {@link com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider}).
   */
  static TextContent psiFragment(TextDomain domain, PsiElement psi, TextRange rangeInPsi) {
    return new TextContentImpl(domain, Collections.singletonList(
      new TextContentImpl.PsiToken(rangeInPsi.substring(psi.getText()), psi, rangeInPsi, TextContentImpl.TokenKind.text)));
  }

  /**
   * @return a new TextContent in the given domain, containing the full {@code psi.getText()}.
   * Consider using {@link #builder()} instead if there's any chance that your language might be used as a data language in templates
   * (see e.g. {@link com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider}).
   */
  static TextContent psiFragment(TextDomain domain, PsiElement psi) {
    return psiFragment(domain, psi, TextRange.from(0, psi.getTextLength()));
  }

  /**
   * A builder allowing to assemble text content taking into account various PSI types and characters to exclude,
   * element manipulators and {@link com.intellij.psi.templateLanguages.OuterLanguageElement}s.
   */
  static TextContentBuilder builder() {
    return TextContentBuilder.FromPsi;
  }

  /**
   * @return a concatenation of several text contents, which must have the same domains.
   */
  static @Nullable TextContent join(List<? extends @NotNull TextContent> components) {
    if (components.isEmpty()) return null;
    if (components.size() == 1) return components.get(0);

    return new TextContentImpl(commonDomain(components), ContainerUtil.flatMap(components, c -> ((TextContentImpl) c).tokens));
  }

  /**
   * @return a concatenation of several text contents (which must have the same domains)
   * with the given whitespace character inserted between each pair of adjacent components.
   */
  @Nullable
  static TextContent joinWithWhitespace(char whitespace, List<? extends @NotNull TextContent> components) {
    if (!Character.isWhitespace(whitespace) && Character.getType(whitespace) != Character.SPACE_SEPARATOR) {
      throw new IllegalArgumentException("Whitespace expected, got " + StringUtil.escapeStringCharacters(String.valueOf(whitespace)));
    }
    if (components.isEmpty()) return null;
    if (components.size() == 1) return components.get(0);

    TextContentImpl.WSTokenInfo wsToken = new TextContentImpl.WSTokenInfo(whitespace);
    return new TextContentImpl(commonDomain(components), StreamEx.of(components)
      .map(c -> ((TextContentImpl) c).tokens)
      .intersperse(Collections.singletonList(wsToken))
      .toFlatList(Function.identity()));
  }

  private static TextDomain commonDomain(List<? extends TextContent> components) {
    TextDomain domain = components.get(0).getDomain();
    if (components.stream().anyMatch(c -> c.getDomain() != domain)) {
      throw new IllegalArgumentException("Joined TextContents should share the same domain");
    }
    return domain;
  }

  /** The kind of exclusion to pass into {@link #excludeRanges(List)} */
  enum ExclusionKind {

    /** Remove a range from this text fragment without any consequences */
    exclude,

    /**
     * Remove a range from this text fragment and remember that there was some markup at the resulting offset.
     * Text checkers may use this information via {@link #markupOffsets()} to produce more relevant warnings.
     */
    markup,

    /**
     * Remove a range from this text fragment assuming that it's replaced with some unknown content.
     * So the text around that range will probably be malformed, and error reporting there might need suppressing.
     */
    unknown
  }

  /** An object representing the range to pass to either {@link #excludeRange} or {@link #markUnknown(TextRange)} */
  class Exclusion {
    public final int start, end;
    public final ExclusionKind kind;

    public Exclusion(int start, int end, ExclusionKind kind) {
      if (start > end) throw new IllegalArgumentException(start + ">" + end);
      this.start = start;
      this.end = end;
      this.kind = kind;
    }

    public Exclusion(int start, int end, boolean markUnknown) {
      this(start, end, markUnknown ? ExclusionKind.unknown : ExclusionKind.exclude);
    }

    @Override
    public String toString() {
      return "(" + (kind == ExclusionKind.markup ? "*" : kind == ExclusionKind.unknown ? "?" : "") + start + "," + end + ")";
    }

    public static Exclusion markUnknown(TextRange range) {
      return new Exclusion(range.getStartOffset(), range.getEndOffset(), true);
    }

    public static Exclusion exclude(TextRange range) {
      return new Exclusion(range.getStartOffset(), range.getEndOffset(), false);
    }
  }

  /** A version of {@link TextContent} with some characters inserted in {@link #markupOffsets()} */
  interface WithMarkup extends CharSequence {

    /** Given an offset in this {@link CharSequence}, return the corresponding offset in the original {@link TextContent} */
    int offsetToOriginal(int offsetWithMarkup);

  }
}
