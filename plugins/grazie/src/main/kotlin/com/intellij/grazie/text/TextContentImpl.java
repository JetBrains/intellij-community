package com.intellij.grazie.text;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class TextContentImpl implements TextContent {
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
    if (tokens.get(0) == WS_TOKEN) tokens.remove(0);
    if (tokens.get(tokens.size() - 1) == WS_TOKEN) tokens.remove(tokens.size() - 1);
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
      if (tokens.get(index) == WS_TOKEN) {
        index--;
      }
    } else {
      if (index > 0 && tokens.get(index - 1) != WS_TOKEN) index--;
    }

    return index;
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
      this.text = text = StringUtil.join(tokens, "");
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
    for (int i = 0; i < tokens.size(); i++) {
      TokenInfo token = tokens.get(i);
      if (token instanceof PsiToken) {
        TextRange psiRange = ((PsiToken) token).psi.getTextRange();
        if (psiRange.containsOffset(fileOffset)) {
          int insidePsi = fileOffset - psiRange.getStartOffset();
          if (((PsiToken) token).rangeInPsi.containsOffset(insidePsi)) {
            return getTokenOffsets()[i] + insidePsi - ((PsiToken) token).rangeInPsi.getStartOffset();
          }
        }
      }
    }
    return null;
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
    return Objects.requireNonNull(containingFile().findElementAt(textOffsetToFile(textOffset)));
  }

  @Override
  public @NotNull List<TextRange> getRangesInFile() {
    return StreamEx.of(tokens).select(PsiToken.class)
      .map(t -> t.rangeInPsi.shiftRight(t.psi.getTextRange().getStartOffset()))
      .filter(r -> !r.isEmpty())
      .toList();
  }

  private PsiFile containingFile() {
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
    return rangeInText.getLength() == 0 ? this : excludeRange(rangeInText, false);
  }

  @Override
  public TextContent markUnknown(TextRange rangeInText) {
    return excludeRange(rangeInText, true);
  }

  @Override
  public boolean intersectsRange(TextRange rangeInFile) {
    return tokens.stream().anyMatch(
      token -> token instanceof PsiToken &&
               ((PsiToken) token).rangeInPsi.shiftRight(((PsiToken) token).psi.getTextRange().getStartOffset()).intersectsStrict(rangeInFile));
  }

  private TextContent excludeRange(TextRange range, boolean unknown) {
    ProgressManager.checkCanceled();
    if (range.getStartOffset() < 0 || range.getEndOffset() > length()) {
      throw new IllegalArgumentException("Text range " + range + " should be between 0 and " + length());
    }
    if (range.getStartOffset() == 0 && range.getEndOffset() == length()) {
      PsiToken first = (PsiToken) tokens.get(0);
      return new TextContentImpl(domain, Collections.singletonList(
        new PsiToken("", first.psi,
          TextRange.from(first.rangeInPsi.getStartOffset(), 0),
          unknown || first.unknown || ((PsiToken) tokens.get(tokens.size() - 1)).unknown)));
    }

    int[] offsets = getTokenOffsets();
    int i1 = findTokenIndex(range.getStartOffset(), offsets, true);
    int i2 = findTokenIndex(range.getEndOffset(), offsets, false);
    PsiToken t1 = (PsiToken) tokens.get(i1);
    PsiToken t2 = (PsiToken) tokens.get(i2);

    List<TokenInfo> newTokens = new ArrayList<>(tokens.subList(0, i1));
    if (range.getStartOffset() > offsets[i1]) {
      newTokens.add(t1.withRange(TextRange.from(t1.rangeInPsi.getStartOffset(), range.getStartOffset() - offsets[i1])));
    }
    if (unknown) {
      newTokens.add(new PsiToken("", t1.psi, TextRange.from(t1.rangeInPsi.getStartOffset() + range.getStartOffset() - offsets[i1], 0), true));
    }
    int token2End = offsets[i2] + t2.length();
    if (token2End > range.getEndOffset()) {
      newTokens.add(t2.withRange(new TextRange(t2.rangeInPsi.getEndOffset() - token2End + range.getEndOffset(), t2.rangeInPsi.getEndOffset())));
    }
    newTokens.addAll(tokens.subList(i2 + 1, tokens.size()));

    return new TextContentImpl(domain, newTokens);
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

  private static boolean isSpace(String text, int start) {
    return Character.isWhitespace(text.charAt(start)) || Character.isSpaceChar(text.charAt(start));
  }

  private static @Nullable TokenInfo merge(TokenInfo t1, TokenInfo t2) {
    if (t1 == WS_TOKEN && t2 == WS_TOKEN) return t1;
    if (t1 instanceof PsiToken && t2 instanceof PsiToken) {
      if (((PsiToken) t1).unknown && ((PsiToken) t2).unknown) {
        return t1;
      }
      if (!((PsiToken) t1).unknown && !((PsiToken) t2).unknown) {
        if (t1.length() == 0) return t2;
        if (t2.length() == 0) return t1;
      }
    }
    return null;
  }

  static final TokenInfo WS_TOKEN = new TokenInfo(" ") {};

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
    final boolean unknown;

    PsiToken(String text, PsiElement psi, TextRange rangeInPsi, boolean unknown) {
      super(text);
      this.psi = psi;
      this.rangeInPsi = rangeInPsi;
      this.unknown = unknown;
      assert rangeInPsi.getLength() == text.length();
      assert !unknown || rangeInPsi.getLength() == 0;
    }

    private int psiStart() {
      return psi.getTextRange().getStartOffset() + rangeInPsi.getStartOffset();
    }

    PsiToken withRange(TextRange range) {
      assert range.getLength() > 0;
      assert !unknown;
      return new PsiToken(range.shiftLeft(rangeInPsi.getStartOffset()).substring(text), psi, range, false);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof PsiToken)) return false;
      PsiToken psiToken = (PsiToken) o;
      return unknown == psiToken.unknown && psi.equals(psiToken.psi) && rangeInPsi.equals(psiToken.rangeInPsi);
    }

    @Override
    public int hashCode() {
      return Objects.hash(psi, rangeInPsi, unknown);
    }
  }
}
