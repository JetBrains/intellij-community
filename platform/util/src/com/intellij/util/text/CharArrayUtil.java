/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.text;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CharArrayUtil {
  private static final int GET_CHARS_THRESHOLD = 10;

  private CharArrayUtil() {
  }

  /**
   * Copies all symbols from the given char sequence to the given array
   * 
   * @param src         source data holder
   * @param dst         output data buffer
   * @param dstOffset   start offset to use within the given output data buffer
   */
  public static void getChars(@NotNull CharSequence src, @NotNull char[] dst, int dstOffset) {
    getChars(src, dst, dstOffset, src.length());
  }

  /**
   * Copies necessary number of symbols from the given char sequence start to the given array.
   *
   * @param src         source data holder
   * @param dst         output data buffer
   * @param dstOffset   start offset to use within the given output data buffer
   * @param len         number of source data symbols to copy to the given buffer
   */
  public static void getChars(@NotNull CharSequence src, @NotNull char[] dst, int dstOffset, int len) {
    getChars(src, dst, 0, dstOffset, len);
  }
  
  /**
   * Copies necessary number of symbols from the given char sequence to the given array.
   * 
   * @param src         source data holder
   * @param dst         output data buffer
   * @param srcOffset   source text offset
   * @param dstOffset   start offset to use within the given output data buffer
   * @param len         number of source data symbols to copy to the given buffer
   */
  public static void getChars(@NotNull CharSequence src, @NotNull char[] dst, int srcOffset, int dstOffset, int len) {
    if (src instanceof CharArrayExternalizable) {
      ((CharArrayExternalizable)src).getChars(srcOffset, srcOffset + len, dst, dstOffset);
      return;
    }
    
    if (len >= GET_CHARS_THRESHOLD) {
      if (src instanceof String) {
        ((String)src).getChars(srcOffset, srcOffset + len, dst, dstOffset);
        return;
      }
      else if (src instanceof CharBuffer) {
        final CharBuffer buffer = (CharBuffer)src;
        final int i = buffer.position();
        buffer.position(i + srcOffset);
        buffer.get(dst, dstOffset, len);
        buffer.position(i);
        return;
      }
      else if (src instanceof CharSequenceBackedByArray) {
        ((CharSequenceBackedByArray)src.subSequence(srcOffset, srcOffset + len)).getChars(dst, dstOffset);
        return;
      }
      else if (src instanceof StringBuffer) {
        ((StringBuffer)src).getChars(srcOffset, srcOffset + len, dst, dstOffset);
        return;
      }
      else if (src instanceof StringBuilder) {
        ((StringBuilder)src).getChars(srcOffset, srcOffset + len, dst, dstOffset);
        return;
      }
    }

    for (int i = 0, j = srcOffset, max = srcOffset + len; j < max && i < dst.length; i++, j++) {
      dst[i + dstOffset] = src.charAt(j);
    }
  }

  @Nullable
  public static char[] fromSequenceWithoutCopying(@Nullable CharSequence seq) {
    if (seq instanceof CharSequenceBackedByArray) {
      return ((CharSequenceBackedByArray)seq).getChars();
    }

    if (seq instanceof CharBuffer) {
      final CharBuffer buffer = (CharBuffer)seq;
      if (buffer.hasArray() && !buffer.isReadOnly() && buffer.arrayOffset() == 0 && buffer.position() == 0) {
        return buffer.array();
      }
    }

    return null;
  }

  /**
   * @return the underlying char[] array if any, or the new chara array if not
   */
  @NotNull
  public static char[] fromSequence(@NotNull CharSequence seq) {
    char[] underlying = fromSequenceWithoutCopying(seq);
    return underlying != null ? Arrays.copyOf(underlying, underlying.length) : fromSequence(seq, 0, seq.length());
  }

  /**
   * @return a new char array containing the sub-sequence's chars 
   */
  @NotNull
  public static char[] fromSequence(@NotNull CharSequence seq, int start, int end) {
    char[] result = new char[end - start];
    getChars(seq, result, start, 0, end - start);
    return result;
  }

  public static int shiftForward(@NotNull CharSequence buffer, int offset, @NotNull String chars) {
    return shiftForward(buffer, offset, buffer.length(), chars);
  }

  /**
   * Tries to find an offset from the {@code [startOffset; endOffset)} interval such that a char from the given buffer is
   * not contained at the given 'chars' string.
   * <p/>
   * Example:
   * {@code buffer="abc", startOffset=0, endOffset = 3, chars="ab". Result: 2}
   * 
   * @param buffer       target buffer which symbols should be checked
   * @param startOffset  start offset to use within the given buffer (inclusive)
   * @param endOffset    end offset to use within the given buffer (exclusive)
   * @param chars        pass-through symbols
   * @return             offset from the {@code [startOffset; endOffset)} which points to a symbol at the given buffer such
   *                     as that that symbol is not contained at the given 'chars';
   *                     {@code endOffset} otherwise
   */
  public static int shiftForward(@NotNull CharSequence buffer, final int startOffset, final int endOffset, @NotNull String chars) {
    for (int offset = startOffset, limit = Math.min(endOffset, buffer.length()); offset < limit; offset++) {
      char c = buffer.charAt(offset);
      int i;
      for (i = 0; i < chars.length(); i++) {
        if (c == chars.charAt(i)) break;
      }
      if (i >= chars.length()) {
        return offset;
      }
    }
    return endOffset;
  }

  public static int shiftForwardCarefully(@NotNull CharSequence buffer, int offset, @NotNull String chars) {
    if (offset + 1 >= buffer.length()) return offset;
    if (!isSuitable(chars, buffer.charAt(offset))) return offset;
    offset++;
    while (true) {
      if (offset >= buffer.length()) return offset - 1;
      char c = buffer.charAt(offset);
      if (!isSuitable(chars, c)) return offset - 1;
      offset++;
    }
  }

  private static boolean isSuitable(@NotNull String chars, final char c) {
    for (int i = 0; i < chars.length(); i++) {
      if (c == chars.charAt(i)) return true;
    }
    return false;
  }

  public static int shiftForward(@NotNull char[] buffer, int offset, @NotNull String chars) {
    return shiftForward(new CharArrayCharSequence(buffer), offset, chars);
  }

  public static int shiftBackward(@NotNull CharSequence buffer, int offset, @NotNull String chars) {
    return shiftBackward(buffer, 0, offset, chars);
  }
  
  public static int shiftBackward(@NotNull CharSequence buffer, int minOffset, int maxOffset, @NotNull String chars) {
    if (maxOffset >= buffer.length()) return maxOffset;
    
    int offset = maxOffset;
    while (true) {
      if (offset < minOffset) break;
      char c = buffer.charAt(offset);
      int i;
      for (i = 0; i < chars.length(); i++) {
        if (c == chars.charAt(i)) break;
      }
      if (i == chars.length()) break;
      offset--;
    }
    return offset;
  }

  public static int shiftBackward(@NotNull char[] buffer, int offset, @NotNull String chars) {
    return shiftBackward(new CharArrayCharSequence(buffer), offset, chars);
  }

  public static int shiftForwardUntil(@NotNull CharSequence buffer, int offset, @NotNull String chars) {
    while (true) {
      if (offset >= buffer.length()) break;
      char c = buffer.charAt(offset);
      int i;
      for (i = 0; i < chars.length(); i++) {
        if (c == chars.charAt(i)) break;
      }
      if (i < chars.length()) break;
      offset++;
    }
    return offset;
  }

  //Commented in order to apply to the green code policy as the method is unused.
  //
  //public static int shiftBackwardUntil(char[] buffer, int offset, String chars) {
  //  return shiftBackwardUntil(new CharArrayCharSequence(buffer), offset, chars);
  //}

  /**
   * Calculates offset that points to the given buffer and has the following characteristics:
   * <p/>
   * <ul>
   *   <li>is less than or equal to the given offset;</li>
   *   <li>
   *      it's guaranteed that all symbols of the given buffer that are located at {@code (returned offset; given offset]}
   *      interval differ from the given symbols;
   *    </li>
   * </ul>
   * <p/>
   * Example: suppose that this method is called with buffer that holds {@code 'test data'} symbols, offset that points
   * to the last symbols and {@code 'sf'} as a chars to exclude. Offset that points to {@code 's'} symbol
   * is returned then, i.e. all symbols of the given buffer that are located after it and not after given offset
   * ({@code 't data'}) are guaranteed to not contain given chars ({@code 'sf'}).
   *
   * @param buffer      symbols buffer to check
   * @param offset      initial symbols buffer offset to use
   * @param chars       chars to exclude
   * @return            offset of the given buffer that guarantees that all symbols at {@code (returned offset; given offset]}
   *                    interval of the given buffer differ from symbols of given {@code 'chars'} arguments;
   *                    given offset is returned if it is outside of given buffer bounds;
   *                    {@code '-1'} is returned if all document symbols that precede given offset differ from symbols
   *                    of the given {@code 'chars to exclude'}
   */
  public static int shiftBackwardUntil(@NotNull CharSequence buffer, int offset, @NotNull String chars) {
    if (offset >= buffer.length()) return offset;
    while (true) {
      if (offset < 0) break;
      char c = buffer.charAt(offset);
      int i;
      for (i = 0; i < chars.length(); i++) {
        if (c == chars.charAt(i)) break;
      }
      if (i < chars.length()) break;
      offset--;
    }
    return offset;
  }

  public static boolean regionMatches(@NotNull char[] buffer, int start, int end, @NotNull CharSequence s) {
    final int len = s.length();
    if (start + len > end) return false;
    if (start < 0) return false;
    for (int i = 0; i < len; i++) {
      if (buffer[start + i] != s.charAt(i)) return false;
    }
    return true;
  }

  public static boolean regionMatches(@NotNull CharSequence buffer, int start, int end, @NotNull CharSequence s) {
    final int len = s.length();
    if (start + len > end) return false;
    if (start < 0) return false;
    
    //if (buffer instanceof String && s instanceof String) {
    //  return ((String)buffer).regionMatches(offset, (String)s, 0, len);
    //}
    
    for (int i = 0; i < len; i++) {
      if (buffer.charAt(start + i) != s.charAt(i)) return false;
    }
    return true;
  }

  public static boolean regionMatches(@NotNull CharSequence s1, int start1, int end1, @NotNull CharSequence s2, int start2, int end2) {
    if (end1-start1 != end2-start2) return false;

    for (int i = start1,j=start2; i < end1; i++,j++) {
      if (s1.charAt(i) != s2.charAt(j)) return false;
    }
    return true;
  }

  public static boolean regionMatches(@NotNull CharSequence buffer, int offset, @NotNull CharSequence s) {
    if (offset + s.length() > buffer.length()) return false;
    if (offset < 0) return false;
    for (int i = 0; i < s.length(); i++) {
      if (buffer.charAt(offset + i) != s.charAt(i)) return false;
    }
    return true;
  }

  public static boolean equals(@NotNull char[] buffer1, int start1, int end1, @NotNull char[] buffer2, int start2, int end2) {
    if (end1 - start1 != end2 - start2) return false;
    for (int i = start1; i < end1; i++) {
      if (buffer1[i] != buffer2[i - start1 + start2]) return false;
    }
    return true;
  }

  public static int indexOf(@NotNull char[] buffer, @NotNull String pattern, int fromIndex) {
    char[] chars = pattern.toCharArray();
    int limit = buffer.length - chars.length + 1;
    if (fromIndex < 0) {
      fromIndex = 0;
    }
    SearchLoop:
    for (int i = fromIndex; i < limit; i++) {
      for (int j = 0; j < chars.length; j++) {
        if (chars[j] != buffer[i + j]) continue SearchLoop;
      }
      return i;
    }
    return -1;
  }

  public static int indexOf(@NotNull CharSequence buffer, @NotNull CharSequence pattern, int fromIndex) {
    return indexOf(buffer, pattern, fromIndex, buffer.length());
  }

  /**
   * Tries to find index of given pattern at the given buffer.
   *
   * @param buffer       characters buffer which contents should be checked for the given pattern
   * @param pattern      target characters sequence to find at the given buffer
   * @param fromIndex    start index (inclusive). Zero is used if given index is negative
   * @param toIndex      end index (exclusive)
   * @return             index of the given pattern at the given buffer if the match is found; {@code -1} otherwise
   */
  public static int indexOf(@NotNull CharSequence buffer, @NotNull CharSequence pattern, int fromIndex, final int toIndex) {
    final int patternLength = pattern.length();
    if (fromIndex < 0) {
      fromIndex = 0;
    }
    int limit = toIndex - patternLength + 1;
    SearchLoop:
    for (int i = fromIndex; i < limit; i++) {
      for (int j = 0; j < patternLength; j++) {
        if (pattern.charAt(j) != buffer.charAt(i + j)) continue SearchLoop;
      }
      return i;
    }
    return -1;
  }

  /**
   * Tries to find index that points to the first location of the given symbol at the given char array at range {@code [from; to)}.
   *
   * @param buffer      target symbols holder to check
   * @param symbol      target symbol which offset should be found
   * @param fromIndex   start index to search (inclusive)
   * @param toIndex     end index to search (exclusive)
   * @return            index that points to the first location of the given symbol at the given char array at range
   *                    {@code [from; to)} if target symbol is found;
   *                    {@code -1} otherwise
   */
  public static int indexOf(@NotNull char[] buffer, final char symbol, int fromIndex, final int toIndex) {
    if (fromIndex < 0) {
      fromIndex = 0;
    }
    for (int i = fromIndex; i < toIndex; i++) {
      if (buffer[i] == symbol) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Tries to find index that points to the last location of the given symbol at the given char array at range {@code [from; to)}.
   *
   * @param buffer      target symbols holder to check
   * @param symbol      target symbol which offset should be found
   * @param fromIndex   start index to search (inclusive)
   * @param toIndex     end index to search (exclusive)
   * @return            index that points to the last location of the given symbol at the given char array at range
   *                    {@code [from; to)} if target symbol is found;
   *                    {@code -1} otherwise
   */
  public static int lastIndexOf(@NotNull char[] buffer, final char symbol, int fromIndex, final int toIndex) {
    if (fromIndex < 0) {
      fromIndex = 0;
    }
    for (int i = toIndex - 1; i >= fromIndex; i--) {
      if (buffer[i] == symbol) {
        return i;
      }
    }
    return -1;
  }

  public static int lastIndexOf(@NotNull CharSequence buffer, @NotNull String pattern, int maxIndex) {
    char[] chars = pattern.toCharArray();
    int end = buffer.length() - chars.length;
    if (maxIndex > end) {
      maxIndex = end;
    }
    SearchLoop:
    for (int i = maxIndex; i >= 0; i--) {
      for (int j = 0; j < chars.length; j++) {
        if (chars[j] != buffer.charAt(i + j)) continue SearchLoop;
      }
      return i;
    }
    return -1;
  }

  public static int lastIndexOf(@NotNull char[] buffer, @NotNull String pattern, int maxIndex) {
    char[] chars = pattern.toCharArray();
    int end = buffer.length - chars.length;
    if (maxIndex > end) {
      maxIndex = end;
    }
    SearchLoop:
    for (int i = maxIndex; i >= 0; i--) {
      for (int j = 0; j < chars.length; j++) {
        if (chars[j] != buffer[i + j]) continue SearchLoop;
      }
      return i;
    }
    return -1;
  }

  public static boolean containsOnlyWhiteSpaces(@Nullable CharSequence chars) {
    if (chars == null) return true;
    for (int i = 0; i < chars.length(); i++) {
      final char c = chars.charAt(i);
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r') continue;
      return false;
    }
    return true;
  }

  @NotNull
  public static TextRange[] getIndents(@NotNull CharSequence charsSequence, int shift) {
    List<TextRange> result = new ArrayList<TextRange>();
    int whitespaceEnd = -1;
    int lastTextFound = 0;
    for(int i = charsSequence.length() - 1; i >= 0; i--){
      final char charAt = charsSequence.charAt(i);
      final boolean isWhitespace = Character.isWhitespace(charAt);
      if(charAt == '\n'){
        result.add(new TextRange(i, (whitespaceEnd >= 0 ? whitespaceEnd : i) + 1).shiftRight(shift));
        whitespaceEnd = -1;
      }
      else if(whitespaceEnd >= 0 ){
        if(isWhitespace){
          continue;
        }
        lastTextFound = result.size();
        whitespaceEnd = -1;
      }
      else if(isWhitespace){
        whitespaceEnd = i;
      } else {
        lastTextFound = result.size();
      }
    }
    if(whitespaceEnd > 0) result.add(new TextRange(0, whitespaceEnd + 1).shiftRight(shift));
    if (lastTextFound < result.size()) {
      result = result.subList(0, lastTextFound);
    }
    return result.toArray(new TextRange[0]);
  }

  public static boolean containLineBreaks(@NotNull CharSequence seq) {
    return containLineBreaks(seq, 0, seq.length());
  }

  public static boolean containLineBreaks(@Nullable CharSequence seq, int fromOffset, int endOffset) {
    if (seq == null) return false;
    for (int i = fromOffset; i < endOffset; i++) {
      final char c = seq.charAt(i);
      if (c == '\n' || c == '\r') return true;
    }
    return false;
  }

  /**
   * Allows to answer if target region of the given text contains only white space symbols (tabulations, white spaces and line feeds).
   * 
   * @param text      text to check
   * @param start     start offset within the given text to check (inclusive)
   * @param end       end offset within the given text to check (exclusive)
   * @return          {@code true} if target region of the given text contains white space symbols only; {@code false} otherwise
   */
  public static boolean isEmptyOrSpaces(@NotNull CharSequence text, int start, int end) {
    for (int i = start; i < end; i++) {
      char c = text.charAt(i);
      if (c != ' ' && c != '\t' && c != '\n') {
        return false;
      }
    }
    return true;
  }

  @NotNull
  public static Reader readerFromCharSequence(@NotNull CharSequence text) {
    char[] chars = fromSequenceWithoutCopying(text);
    //noinspection IOResourceOpenedButNotSafelyClosed
    return chars == null ? new CharSequenceReader(text.toString()) : new UnsyncCharArrayReader(chars, 0, text.length());
  }

  @NotNull
  public static ImmutableCharSequence createImmutableCharSequence(@NotNull CharSequence sequence) {
    return ImmutableText.valueOf(sequence);
  }
}
