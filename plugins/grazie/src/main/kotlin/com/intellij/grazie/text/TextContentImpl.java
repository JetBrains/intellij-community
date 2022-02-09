package com.intellij.grazie.text;

import com.intellij.grazie.grammar.strategy.StrategyUtils;
import com.intellij.grazie.utils.Text;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import kotlin.ranges.IntRange;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

class TextContentImpl extends UserDataHolderBase implements TextContent {
  private final TextDomain domain;
  final List<TokenInfo> tokens;
  private volatile String text;
  private volatile int[] tokenOffsets;

  TextContentImpl(TextDomain domain, List<TokenInfo> _tokens) {
    this.domain = domain;
    tokens = new ArrayList<>(_tokens.size());
    if (_tokens.isEmpty()) {
      throw new IllegalArgumentException("No tokens");
    }
    for (TokenInfo token : _tokens) {
      if (!tokens.isEmpty()) {
        TokenInfo merged = merge(tokens.get(tokens.size() - 1), token);
        if (merged != null) {
          tokens.set(tokens.size() - 1, merged);
          continue;
        }
      }
      tokens.add(token);
    }
    if (tokens.get(0) instanceof WSTokenInfo) tokens.remove(0);
    if (tokens.get(tokens.size() - 1) instanceof WSTokenInfo) tokens.remove(tokens.size() - 1);
    if (tokens.isEmpty()) {
      throw new IllegalArgumentException("There should be at least one non-whitespace token");
    }

    List<TextRange> ranges = getRangesInFile();
    if (!ContainerUtil.sorted(ranges, Segment.BY_START_OFFSET_THEN_END_OFFSET).equals(ranges)) {
      throw new IllegalArgumentException("TextContent fragments should be ordered by the offset ascending: " + ranges);
    }
  }

  private int findTokenIndex(int textOffset, int[] tokenOffsets, boolean leanForward) {
    if (textOffset < 0 || textOffset > length()) {
      throw new IllegalArgumentException("Text offset " + textOffset + " should be between 0 and " + length());
    }
    int index = Arrays.binarySearch(tokenOffsets, textOffset);
    if (index < 0) return -index - 2;

    if (leanForward) {
      while (index < tokens.size() - 1 && tokens.get(index).length() == 0) index++;
      if (tokens.get(index) instanceof WSTokenInfo) {
        index--;
      }
    } else {
      if (index > 0 && !(tokens.get(index - 1) instanceof WSTokenInfo)) index--;
    }

    return index;
  }

  private int findPsiTokenIndex(int fileOffset) {
    return ObjectUtils.binarySearch(0, tokens.size(), mid -> {
      int psiTokenIndex = mid;
      while (!(tokens.get(psiTokenIndex) instanceof PsiToken)) psiTokenIndex--;
      var tokenRange = ((PsiToken) tokens.get(psiTokenIndex)).rangeInFile;
      if (tokenRange.containsOffset(fileOffset)) return mid == psiTokenIndex ? 0 : 1;
      return tokenRange.getEndOffset() < fileOffset ? -1 : 1;
    });
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TextContentImpl)) return false;
    TextContentImpl that = (TextContentImpl) o;
    return domain == that.domain && tokens.equals(that.tokens);
  }

  @Override
  public int hashCode() {
    return Objects.hash(domain, tokens);
  }

  @Override
  public TextDomain getDomain() {
    return domain;
  }

  @Override
  public String toString() {
    String text = this.text;
    if (text == null) {
      this.text = text = StringUtil.join(tokens, t -> t.text, "");
    }
    return text;
  }

  @Override
  public char charAt(int index) {
    return toString().charAt(index);
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    return toString().subSequence(start, end);
  }

  @Override
  public int length() {
    return toString().length();
  }

  @Override
  public int textOffsetToFile(int textOffset) {
    String text = toString();
    char prev = textOffset <= 0 ? ' ' : text.charAt(textOffset - 1);
    char next = textOffset >= text.length() ? ' ' : text.charAt(textOffset);
    boolean leanForward = (Character.isWhitespace(prev) || !Character.isWhitespace(next)) &&
                          (!Character.isLetterOrDigit(prev) || Character.isLetterOrDigit(next));
    return textOffsetToFile(textOffset, leanForward);
  }

  @Override
  public int textOffsetToFile(int textOffset, boolean leanForward) {
    int[] offsets = getTokenOffsets();
    int tokenIndex = findTokenIndex(textOffset, offsets, leanForward);
    return ((PsiToken) tokens.get(tokenIndex)).psiStart() + (textOffset - offsets[tokenIndex]);
  }

  @Override
  public Integer fileOffsetToText(int fileOffset) {
    int index = findPsiTokenIndex(fileOffset);
    return index < 0 ? null : getTokenOffsets()[index] + fileOffset - ((PsiToken) tokens.get(index)).rangeInFile.getStartOffset();
  }

  @Override
  public @Nullable TextRange fileRangeToText(TextRange fileRange) {
    Integer start = fileOffsetToText(fileRange.getStartOffset());
    Integer end = fileOffsetToText(fileRange.getEndOffset());
    return start == null || end == null ? null : new TextRange(start, end);
  }

  @Override
  public @NotNull PsiElement getCommonParent() {
    return Objects.requireNonNull(
      PsiTreeUtil.findCommonParent(new ArrayList<>(ContainerUtil.map2SetNotNull(tokens, t -> t instanceof PsiToken ? ((PsiToken) t).psi : null))));
  }

  @Override
  public @NotNull PsiElement findPsiElementAt(int textOffset) {
    return Objects.requireNonNull(getContainingFile().findElementAt(textOffsetToFile(textOffset)));
  }

  @Override
  public @NotNull List<TextRange> getRangesInFile() {
    return StreamEx.of(tokens).select(PsiToken.class).map(t -> t.rangeInFile).filter(r -> !r.isEmpty()).toList();
  }

  @Override
  public @NotNull PsiFile getContainingFile() {
    for (TokenInfo token : tokens) {
      if (token instanceof PsiToken) {
        return ((PsiToken) token).psi.getContainingFile();
      }
    }
    throw new IllegalStateException("No PSI tokens");
  }

  @Override
  public boolean hasUnknownFragmentsIn(TextRange rangeInText) {
    int[] offsets = getTokenOffsets();
    int start = findTokenIndex(rangeInText.getStartOffset(), offsets, false);
    int end = findTokenIndex(rangeInText.getEndOffset(), offsets, true);
    for (int i = start; i <= end; i++) {
      TokenInfo token = tokens.get(i);
      if (token instanceof PsiToken && ((PsiToken) token).unknown && rangeInText.containsOffset(offsets[i])) {
        return true;
      }
    }
    return false;
  }

  @Override
  public TextContent excludeRange(TextRange rangeInText) {
    return rangeInText.getLength() == 0 ? this : excludeRanges(List.of(Exclusion.exclude(rangeInText)));
  }

  @Override
  public TextContent markUnknown(TextRange rangeInText) {
    return excludeRanges(List.of(Exclusion.markUnknown(rangeInText)));
  }

  @Override
  public boolean intersectsRange(TextRange rangeInFile) {
    int start = findPsiTokenIndex(rangeInFile.getStartOffset());
    if (start < 0) start = -start - 1;
    for (int i = start; i < tokens.size(); i++) {
      TokenInfo token = tokens.get(i);
      if (!(token instanceof PsiToken)) continue;

      TextRange tokenRange = ((PsiToken) token).rangeInFile;
      if (tokenRange.intersectsStrict(rangeInFile)) return true;
      if (tokenRange.getStartOffset() >= rangeInFile.getEndOffset()) break;
    }
    return false;
  }

  @Override
  public TextContent excludeRanges(List<Exclusion> ranges) {
    ProgressManager.checkCanceled();
    if (ranges.isEmpty()) return this;

    if (ranges.get(0).start < 0 || ranges.get(ranges.size() - 1).end > length()) {
      throw new IllegalArgumentException("Text ranges " + ranges + " should be between 0 and " + length());
    }
    for (int i = 1; i < ranges.size(); i++) {
      if (ranges.get(i - 1).end > ranges.get(i).start) {
        throw new IllegalArgumentException("Ranges should be sorted and non-intersecting: " + ranges);
      }
    }

    int[] offsets = getTokenOffsets();
    List<Exclusion>[] affectingExclusions = getAffectingExclusions(ranges, offsets);

    List<TokenInfo> newTokens = new ArrayList<>();
    for (int i = 0; i < tokens.size(); i++) {
      List<Exclusion> affecting = affectingExclusions[i];
      TokenInfo token = tokens.get(i);
      if (affecting == null) {
        newTokens.add(token);
      } else if (token instanceof PsiToken) {
        newTokens.addAll(((PsiToken)token).splitToken(offsets[i], affecting));
      }
    }

    if (newTokens.isEmpty()) {
      PsiToken first = (PsiToken) tokens.get(0);
      newTokens.add(new PsiToken("", first.psi, TextRange.from(first.rangeInPsi.getStartOffset(), 0),
                                 first.unknown || ((PsiToken)tokens.get(tokens.size() - 1)).unknown));
    }
    
    return new TextContentImpl(domain, newTokens);
  }

  private @Nullable List<Exclusion> @NotNull [] getAffectingExclusions(List<Exclusion> ranges, int[] offsets) {
    @SuppressWarnings("unchecked") List<Exclusion>[] affectingExclusions = new List[tokens.size()];

    for (Exclusion range : ranges) {
      boolean emptyRange = range.start == range.end;
      if (emptyRange && !range.markUnknown) continue;

      int i1 = findTokenIndex(range.start, offsets, true);
      int i2 = findTokenIndex(range.end, offsets, emptyRange);
      while (i2 > 0 && tokens.get(i2).length() == 0) i2--;

      for (int j = i1; j <= i2; j++) {
        List<Exclusion> affecting = affectingExclusions[j];
        if (affecting == null) affectingExclusions[j] = affecting = new ArrayList<>();
        affecting.add(range);
      }
    }
    return affectingExclusions;
  }

  private int[] getTokenOffsets() {
    int[] offsets = tokenOffsets;
    if (offsets == null) {
      tokenOffsets = offsets = calcTokenOffsets();
    }
    return offsets;
  }

  private int[] calcTokenOffsets() {
    int[] offsets = new int[tokens.size()];
    int tokenStart = 0;
    for (int i = 0; i < tokens.size(); i++) {
      TokenInfo info = tokens.get(i);
      offsets[i] = tokenStart;
      tokenStart += info.length();
    }
    return offsets;
  }

  @Override
  public TextContent trimWhitespace() {
    String text = toString();
    int start = 0;
    int end = text.length();
    while (start < end && isSpace(text, start)) start++;
    while (start < end && isSpace(text, end - 1)) end--;
    if (start >= end) {
      return null;
    }
    if (start > 0 || end < text.length()) {
      return excludeRange(new TextRange(end, text.length())).excludeRange(new TextRange(0, start));
    }
    return this;
  }

  @Override
  public TextContent removeIndents(Set<Character> indentChars) {
    LinkedHashSet<IntRange> ranges = StrategyUtils.INSTANCE.indentIndexes(this, indentChars);
    List<Exclusion> exclusions = StreamEx.of(ranges)
      .sorted(Comparator.comparingInt(IntRange::getFirst))
      .map(range -> {
        int end = range.getEndInclusive() + 1;
        return new Exclusion(range.getStart(), end, hasUnknownFragmentsIn(new TextRange(range.getStart(), end)));
      })
      .toList();
    return excludeRanges(exclusions);
  }

  @Override
  public TextContent removeLineSuffixes(Set<Character> suffixChars) {
    if (suffixChars.isEmpty()) return this;

    Pattern pattern = Pattern.compile("(" + Strings.join(suffixChars, c -> Pattern.quote(String.valueOf(c)), "|") + ")(?=\n)");
    return excludeRanges(ContainerUtil.map(Text.allOccurrences(pattern, this), Exclusion::exclude));
  }

  private static boolean isSpace(String text, int start) {
    return Character.isWhitespace(text.charAt(start)) || Character.isSpaceChar(text.charAt(start));
  }

  private static @Nullable TokenInfo merge(TokenInfo t1, TokenInfo t2) {
    if (t1 instanceof WSTokenInfo && t2 instanceof WSTokenInfo) return t1;
    if (t1 instanceof PsiToken && t2 instanceof PsiToken) return mergePsiTokens((PsiToken)t1, (PsiToken)t2);
    return null;
  }

  private static TokenInfo mergePsiTokens(PsiToken t1, PsiToken t2) {
    if (t1.unknown && t2.unknown) {
      return t1;
    }
    if (!t1.unknown && !t2.unknown) {
      if (t1.length() == 0) return t2;
      if (t2.length() == 0) return t1;
      if (t1.psi == t2.psi && t1.rangeInPsi.getStartOffset() + t1.length() == t2.rangeInPsi.getStartOffset()) {
        return new PsiToken(t1.text + t2.text, t1.psi, t1.rangeInPsi.union(t2.rangeInPsi), false);
      }
    }
    return null;
  }

  abstract static class TokenInfo {
    final String text;

    TokenInfo(String text) {
      this.text = text;
    }

    int length() {
      return text.length();
    }

    @Override
    public String toString() {
      return text;
    }
  }

  static class PsiToken extends TokenInfo {
    final PsiElement psi;
    final TextRange rangeInPsi;
    final TextRange rangeInFile;
    final boolean unknown;

    PsiToken(String text, PsiElement psi, TextRange rangeInPsi, boolean unknown) {
      super(text);
      this.psi = psi;
      this.rangeInPsi = rangeInPsi;
      this.rangeInFile = rangeInPsi.shiftRight(psi.getTextRange().getStartOffset());
      this.unknown = unknown;
      assert rangeInPsi.getLength() == text.length();
      assert !unknown || rangeInPsi.getLength() == 0;
    }

    private int psiStart() {
      return rangeInFile.getStartOffset();
    }

    private PsiToken withRange(TextRange range) {
      assert range.getLength() > 0;
      assert !unknown;
      return new PsiToken(range.shiftLeft(rangeInPsi.getStartOffset()).substring(text), psi, range, false);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof PsiToken)) return false;
      PsiToken psiToken = (PsiToken) o;
      return unknown == psiToken.unknown && psi.equals(psiToken.psi) && (unknown || rangeInPsi.equals(psiToken.rangeInPsi));
    }

    @Override
    public int hashCode() {
      return Objects.hash(psi, rangeInPsi, unknown);
    }

    @Override
    public String toString() {
      return unknown ? "?" : super.toString();
    }

    private List<PsiToken> splitToken(int tokenStart, List<Exclusion> affecting) {
      int tokenEnd = tokenStart + length();
      if (affecting.size() == 1 && affecting.get(0).start < tokenStart && affecting.get(0).end > tokenEnd) {
        return Collections.emptyList();
      }

      List<PsiToken> shreds = new ArrayList<>();
      int startInPsi = rangeInPsi.getStartOffset();
      int prevEnd = tokenStart;
      for (Exclusion range : affecting) {
        if (range.start > prevEnd) {
          shreds.add(withRange(TextRange.from(startInPsi + prevEnd - tokenStart, range.start - prevEnd)));
        }
        if (range.markUnknown) {
          shreds.add(new PsiToken("", psi, TextRange.from(startInPsi + range.start - tokenStart, 0), true));
        }
        prevEnd = range.end;
      }
      Exclusion lastRange = affecting.get(affecting.size() - 1);
      if (tokenEnd > lastRange.end) {
        shreds.add(withRange(new TextRange(startInPsi + lastRange.end - tokenStart, startInPsi + length())));
      }
      return shreds;
    }
  }

  static class WSTokenInfo extends TokenInfo {
    WSTokenInfo(char ws) { super(String.valueOf(ws)); }
  }
}
