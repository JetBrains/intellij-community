/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * <p>Utility class to guess the encoding of a given byte array.
 * The guess is unfortunately not 100% sure. Especially for 8-bit charsets.
 * It's not possible to know which 8-bit charset is used.
 * We will then infer that the charset encountered is the same as the default standard charset.</p>
 *
 * <p>On the other hand, unicode files encoded in UTF-16 (low or big endian) or UTF-8 files
 * with a Byte Order Marker are easy to find. For UTF-8 files with no BOM, if the buffer
 * is wide enough, it's easy to guess.</p>
 *
 * <p>Tested against a complicated UTF-8 file, Sun's implementation does not render bad UTF-8
 * constructs as expected by the specification. But with a buffer wide enough, the method guessEncoding()
 * did behave correctly and recognized the UTF-8 charset.</p>
 *
 * <p>A byte buffer of 4KB or 8KB is sufficient to be able to guessEncoding the encoding.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * // guess the encoding
 * Charset guessedCharset = CharsetToolkit.guessEncoding(file, 4096);
 *
 * // create a reader with the charset we've just discovered
 * FileInputStream fis = new FileInputStream(file);
 * InputStreamReader isr = new InputStreamReader(fis, guessedCharset);
 * BufferedReader br = new BufferedReader(isr);
 *
 * // read the file content
 * String line;
 * while ((line = br.readLine())!= null)
 * {
 *     System.out.println(line);
 * }
 * </pre>
 * <p>An interesting improvement would be to create a custom {@code InputStream} that has a
 * method discovering the {@code Charset} of the underlying file. Thus, we would not have to
 * read the beginning of the file twice: once for guessing the encoding, the second time for reading
 * its content. Therefore, we could englobe this stream within an {@code InputStreamReader}.</p>
 *
 * <p>Date: 18 juil. 2002</p>
 * @author Guillaume LAFORGE
 */
public class CharsetToolkit {
  @NonNls public static final String UTF8 = "UTF-8";
  public static final Charset UTF8_CHARSET = Charset.forName(UTF8);
  public static final Charset UTF_16LE_CHARSET = Charset.forName("UTF-16LE");
  public static final Charset UTF_16BE_CHARSET = Charset.forName("UTF-16BE");
  public static final Charset UTF_32BE_CHARSET = Charset.forName("UTF-32BE");
  public static final Charset UTF_32LE_CHARSET = Charset.forName("UTF-32LE");
  public static final Charset UTF_16_CHARSET = Charset.forName("UTF-16");
  public static final Charset US_ASCII_CHARSET = Charset.forName("US-ASCII");
  public static final Charset ISO_8859_1_CHARSET = Charset.forName("ISO-8859-1");
  public static final Charset WIN_1251_CHARSET = Charset.forName("windows-1251");
  private static final byte FF = (byte)0xff;
  private static final byte FE = (byte)0xfe;
  private static final byte EF = (byte)0xef;
  private static final byte BB = (byte)0xbb;
  private static final byte BF = (byte)0xbf;
  private static final int BINARY_THRESHOLD = 9; // characters with codes below this considered to be binary

  private final byte[] buffer;
  @NotNull
  private final Charset defaultCharset;
  private boolean enforce8Bit;

  public static final byte[] UTF8_BOM = {0xffffffef, 0xffffffbb, 0xffffffbf};
  public static final byte[] UTF16LE_BOM = {-1, -2, };
  public static final byte[] UTF16BE_BOM = {-2, -1, };
  public static final byte[] UTF32BE_BOM = {0, 0, -2, -1, };
  public static final byte[] UTF32LE_BOM = {-1, -2, 0, 0 };
  @NonNls public static final String FILE_ENCODING_PROPERTY = "file.encoding";

  @NonNls private static final Map<Charset, byte[]> CHARSET_TO_MANDATORY_BOM = new THashMap<Charset, byte[]>(4);
  static {
    CHARSET_TO_MANDATORY_BOM.put(UTF_16LE_CHARSET, UTF16LE_BOM);
    CHARSET_TO_MANDATORY_BOM.put(UTF_16BE_CHARSET, UTF16BE_BOM);
    CHARSET_TO_MANDATORY_BOM.put(UTF_32BE_CHARSET, UTF32BE_BOM);
    CHARSET_TO_MANDATORY_BOM.put(UTF_32LE_CHARSET, UTF32LE_BOM);
  }

  /**
   * Constructor of the {@code CharsetToolkit} utility class.
   *
   * @param buffer the byte buffer of which we want to know the encoding.
   */
  public CharsetToolkit(@NotNull byte[] buffer) {
    this.buffer = buffer;
    defaultCharset = getDefaultSystemCharset();
  }

  /**
   * Constructor of the {@code CharsetToolkit} utility class.
   *
   * @param buffer the byte buffer of which we want to know the encoding.
   * @param defaultCharset the default Charset to use in case an 8-bit charset is recognized.
   */
  public CharsetToolkit(@NotNull byte[] buffer, @NotNull Charset defaultCharset) {
    this.buffer = buffer;
    this.defaultCharset = defaultCharset;
  }

  @NotNull
  public static InputStream inputStreamSkippingBOM(@NotNull InputStream stream) throws IOException {
    if (!stream.markSupported()) {
      stream = new BufferedInputStream(stream);
    }

    stream.mark(4);
    boolean mustReset = true;
    try {
      int ret = stream.read();
      if (ret == -1) {
        return stream; // no bom
      }
      byte b0 = (byte)ret;
      if (b0 != EF && b0 != FF && b0 != FE && b0 != 0) return stream; // no bom

      ret = stream.read();
      if (ret == -1) {
        return stream; // no bom
      }
      byte b1 = (byte)ret;
      if (b0 == FF && b1 == FE) {
        stream.mark(2);
        ret = stream.read();
        if (ret == -1) {
          return stream;  // utf-16 LE
        }
        byte b2 = (byte)ret;
        if (b2 != 0) {
          return stream; // utf-16 LE
        }
        ret = stream.read();
        if (ret == -1) {
          return stream;
        }
        byte b3 = (byte)ret;
        if (b3 != 0) {
          return stream; // utf-16 LE
        }

        // utf-32 LE
        mustReset = false;
        return stream;
      }
      if (b0 == FE && b1 == FF) {
        mustReset = false;
        return stream; // utf-16 BE
      }
      if (b0 == EF && b1 == BB) {
        ret = stream.read();
        if (ret == -1) {
          return stream; // no bom
        }
        byte b2 = (byte)ret;
        if (b2 == BF) {
          mustReset = false;
          return stream; // utf-8 bom
        }

        // no bom
        return stream;
      }

      if (b0 == 0 && b1 == 0) {
        ret = stream.read();
        if (ret == -1) {
          return stream;  // no bom
        }
        byte b2 = (byte)ret;
        if (b2 != FE) {
          return stream; // no bom
        }
        ret = stream.read();
        if (ret == -1) {
          return stream;  // no bom
        }
        byte b3 = (byte)ret;
        if (b3 != FF) {
          return stream; // no bom
        }

        mustReset = false;
        return stream; // UTF-32 BE
      }

      // no bom
      return stream;
    }
    finally {
      if (mustReset) stream.reset();
    }
  }

  /**
   * If US-ASCII is recognized, enforce to return the default encoding, rather than US-ASCII.
   * It might be a file without any special character in the range 128-255, but that may be or become
   * a file encoded with the default {@code charset} rather than US-ASCII.
   *
   * @param enforce a boolean specifying the use or not of US-ASCII.
   */
  public void setEnforce8Bit(boolean enforce) {
    enforce8Bit = enforce;
  }

  /**
   * Gets the enforce8Bit flag, in case we do not want to ever get a US-ASCII encoding.
   *
   * @return a boolean representing the flag of use of US-ASCII.
   */
  public boolean getEnforce8Bit() {
    return enforce8Bit;
  }

  /**
   * Retrieves the default Charset
   */
  @NotNull
  public Charset getDefaultCharset() {
    return defaultCharset;
  }

  /**
   * <p>Guess the encoding of the provided buffer.</p>
   * If Byte Order Markers are encountered at the beginning of the buffer, we immediately
   * return the charset implied by this BOM. Otherwise, the file would not be a human
   * readable text file.</p>
   *
   * <p>If there is no BOM, this method tries to discern whether the file is UTF-8 or not.
   * If it is not UTF-8, we assume the encoding is the default system encoding
   * (of course, it might be any 8-bit charset, but usually, an 8-bit charset is the default one).</p>
   *
   * <p>It is possible to discern UTF-8 thanks to the pattern of characters with a multi-byte sequence.</p>
   * <pre>
   * UCS-4 range (hex.)        UTF-8 octet sequence (binary)
   * 0000 0000-0000 007F       0xxxxxxx
   * 0000 0080-0000 07FF       110xxxxx 10xxxxxx
   * 0000 0800-0000 FFFF       1110xxxx 10xxxxxx 10xxxxxx
   * 0001 0000-001F FFFF       11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
   * 0020 0000-03FF FFFF       111110xx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
   * 0400 0000-7FFF FFFF       1111110x 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
   * </pre>
   * <p>With UTF-8, 0xFE and 0xFF never appear.</p>
   *
   * @return the Charset recognized.
   */
  public Charset guessEncoding(int startOffset, int endOffset, @NotNull Charset defaultCharset) {
    // if the file has a Byte Order Marker, we can assume the file is in UTF-xx
    // otherwise, the file would not be human readable
    Charset charset = guessFromBOM();
    if (charset != null) return charset;
    GuessedEncoding encoding = guessFromContent(startOffset, endOffset);
    switch (encoding) {
      case SEVEN_BIT:
        // if no byte with an high order bit set, the encoding is US-ASCII
        // (it might have been UTF-7, but this encoding is usually internally used only by mail systems)
        // returns the default charset rather than US-ASCII if the enforce8Bit flag is set.
        return enforce8Bit ? defaultCharset : Charset.forName("US-ASCII");
      case INVALID_UTF8:
        return defaultCharset;
      case VALID_UTF8:
        return UTF8_CHARSET;
      case BINARY:
        break;
      default:
        break;
    }
    return null;
  }

  @NotNull
  public static String bytesToString(@NotNull byte[] bytes, @NotNull final Charset defaultCharset) {
    Charset charset = new CharsetToolkit(bytes, defaultCharset).guessEncoding(bytes.length);
    if (charset == null) charset = defaultCharset; // binary content. This is silly but method contract says to return something anyway
    return decodeString(bytes, charset);
  }

  @NotNull
  public static String decodeString(@NotNull byte[] bytes, @NotNull final Charset charset) {
    int bomLength = getBOMLength(bytes, charset);
    final CharBuffer charBuffer = charset.decode(ByteBuffer.wrap(bytes, bomLength, bytes.length - bomLength));
    return charBuffer.toString();
  }

  @Nullable
  public static String tryDecodeString(@NotNull byte[] bytes, @NotNull final Charset charset) {
    try {
      int bomLength = getBOMLength(bytes, charset);
      ByteBuffer buffer = ByteBuffer.wrap(bytes, bomLength, bytes.length - bomLength);
      CharsetDecoder decoder = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
      return decoder.decode(buffer).toString();
    }
    catch (CharacterCodingException e) {
      return null;
    }
  }

  public enum GuessedEncoding {
    SEVEN_BIT,     // ASCII
    VALID_UTF8,    // UTF-8
    INVALID_UTF8,  // invalid UTF
    BINARY         // binary
  }

  @NotNull
  public GuessedEncoding guessFromContent(int guess_length) {
    return guessFromContent(0, guess_length);
  }

  @NotNull
  public GuessedEncoding guessFromContent(int startOffset, int endOffset) {
    // if a byte has its most significant bit set, the file is in UTF-8 or in the default encoding
    // otherwise, the file is in US-ASCII
    boolean highOrderBit = false;

    // if the file is in UTF-8, high order bytes must have a certain value, in order to be valid
    // if it's not the case, we can assume the encoding is the default encoding of the system
    boolean validU8Char = true;

    // true if char bytes < BINARY_THRESHOLD occurred
    boolean hasBinary = false;

    int end = Math.min(buffer.length, endOffset);
    int i = startOffset;
    while (i < end) {
      byte b0 = buffer[i];
      byte b1 = i + 1 >= end ? 0 : buffer[i + 1];
      byte b2 = i + 2 >= end ? 0 : buffer[i + 2];
      byte b3 = i + 3 >= end ? 0 : buffer[i + 3];
      byte b4 = i + 4 >= end ? 0 : buffer[i + 4];
      byte b5 = i + 5 >= end ? 0 : buffer[i + 5];
      if (b0 < 0) {
        // a high order bit was encountered, thus the encoding is not US-ASCII
        // it may be either an 8-bit encoding or UTF-8
        highOrderBit = true;
        // a two-bytes sequence was encountered
        if (isTwoBytesSequence(b0)) {
          // there must be one continuation byte of the form 10xxxxxx,
          // otherwise the following characters is not a valid UTF-8 construct
          if (!isContinuationChar(b1)) {
            validU8Char = false;
          }
          else {
            i++;
          }
        }
        // a three-bytes sequence was encountered
        else if (isThreeBytesSequence(b0)) {
          // there must be two continuation bytes of the form 10xxxxxx,
          // otherwise the following characters is not a valid UTF-8 construct
          if (!(isContinuationChar(b1) && isContinuationChar(b2))) {
            validU8Char = false;
          }
          else {
            i += 2;
          }
        }
        // a four-bytes sequence was encountered
        else if (isFourBytesSequence(b0)) {
          // there must be three continuation bytes of the form 10xxxxxx,
          // otherwise the following characters is not a valid UTF-8 construct
          if (!(isContinuationChar(b1) && isContinuationChar(b2) && isContinuationChar(b3))) {
            validU8Char = false;
          }
          else {
            i += 3;
          }
        }
        // a five-bytes sequence was encountered
        else if (isFiveBytesSequence(b0)) {
          // there must be four continuation bytes of the form 10xxxxxx,
          // otherwise the following characters is not a valid UTF-8 construct
          if (!(isContinuationChar(b1) && isContinuationChar(b2) && isContinuationChar(b3) && isContinuationChar(b4))) {
            validU8Char = false;
          }
          else {
            i += 4;
          }
        }
        // a six-bytes sequence was encountered
        else if (isSixBytesSequence(b0)) {
          // there must be five continuation bytes of the form 10xxxxxx,
          // otherwise the following characters is not a valid UTF-8 construct
          if (!(isContinuationChar(b1) &&
                isContinuationChar(b2) &&
                isContinuationChar(b3) &&
                isContinuationChar(b4) &&
                isContinuationChar(b5))) {
            validU8Char = false;
          }
          else {
            i += 5;
          }
        }
        else {
          validU8Char = false;
        }
      }
      else if (b0 < BINARY_THRESHOLD) {
        hasBinary = true;
      }
      if (!validU8Char) break;
      i++;
    }

    if (!highOrderBit && !hasBinary) {
      return GuessedEncoding.SEVEN_BIT;
    }
    // finally, if it's not UTF-8 nor US-ASCII
    if (!validU8Char) return GuessedEncoding.INVALID_UTF8;
    if (hasBinary) return GuessedEncoding.BINARY;

    // if no invalid UTF-8 were encountered, we can assume the encoding is UTF-8,
    // otherwise the file would not be human readable
    return GuessedEncoding.VALID_UTF8;
  }

  @Nullable
  public Charset guessFromBOM() {
    return guessFromBOM(buffer);
  }

  @Nullable
  public static Charset guessFromBOM(@NotNull byte[] buffer) {
    if (hasUTF8Bom(buffer)) return UTF8_CHARSET;
    if (hasUTF32BEBom(buffer)) return UTF_32BE_CHARSET;
    if (hasUTF32LEBom(buffer)) return UTF_32LE_CHARSET;
    if (hasUTF16LEBom(buffer)) return UTF_16LE_CHARSET;
    if (hasUTF16BEBom(buffer)) return UTF_16BE_CHARSET;

    return null;
  }

  public Charset guessEncoding(int guess_length) {
    return guessEncoding(0,guess_length, defaultCharset);
  }

  public static Charset guessEncoding(@NotNull File f, int bufferLength, @NotNull Charset defaultCharset) throws IOException {
    byte[] buffer = new byte[bufferLength];
    int read;
    FileInputStream fis = new FileInputStream(f);
    try {
      read = fis.read(buffer);
    }
    finally {
      fis.close();
    }
    CharsetToolkit toolkit = new CharsetToolkit(buffer, defaultCharset);
    return toolkit.guessEncoding(read);
  }

  /**
   * If the byte has the form 10xxxxx, then it's a continuation byte of a multiple byte character;
   *
   * @param b a byte.
   * @return true if it's a continuation char.
   */
  private static boolean isContinuationChar(byte b) {
    return b <= -65;
  }

  /**
   * If the byte has the form 110xxxx, then it's the first byte of a two-bytes sequence character.
   *
   * @param b a byte.
   * @return true if it's the first byte of a two-bytes sequence.
   */
  private static boolean isTwoBytesSequence(byte b) {
    return -64 <= b && b <= -33;
  }

  /**
   * If the byte has the form 1110xxx, then it's the first byte of a three-bytes sequence character.
   *
   * @param b a byte.
   * @return true if it's the first byte of a three-bytes sequence.
   */
  private static boolean isThreeBytesSequence(byte b) {
    return -32 <= b && b <= -17;
  }

  /**
   * If the byte has the form 11110xx, then it's the first byte of a four-bytes sequence character.
   *
   * @param b a byte.
   * @return true if it's the first byte of a four-bytes sequence.
   */
  private static boolean isFourBytesSequence(byte b) {
    return -16 <= b && b <= -9;
  }

  /**
   * If the byte has the form 11110xx, then it's the first byte of a five-bytes sequence character.
   *
   * @param b a byte.
   * @return true if it's the first byte of a five-bytes sequence.
   */
  private static boolean isFiveBytesSequence(byte b) {
    return -8 <= b && b <= -5;
  }

  /**
   * If the byte has the form 1110xxx, then it's the first byte of a six-bytes sequence character.
   *
   * @param b a byte.
   * @return true if it's the first byte of a six-bytes sequence.
   */
  private static boolean isSixBytesSequence(byte b) {
    return -4 <= b && b <= -3;
  }

  /**
   * Retrieve the default charset of the system.
   *
   * @return the default {@code Charset}.
   */
  @NotNull
  public static Charset getDefaultSystemCharset() {
    return Charset.defaultCharset();
  }

  /**
   * Has a Byte Order Marker for UTF-8 (Used by Microsoft's Notepad and other editors).
   *
   * @param bom a buffer.
   * @return true if the buffer has a BOM for UTF8.
   */
  public static boolean hasUTF8Bom(@NotNull byte[] bom) {
    return ArrayUtil.startsWith(bom, UTF8_BOM);
  }

  /**
   * Has a Byte Order Marker for UTF-16 Low Endian
   * (ucs-2le, ucs-4le, and ucs-16le).
   *
   * @param bom a buffer.
   * @return true if the buffer has a BOM for UTF-16 Low Endian.
   */
  public static boolean hasUTF16LEBom(@NotNull byte[] bom) {
    return ArrayUtil.startsWith(bom, UTF16LE_BOM);
  }

  /**
   * Has a Byte Order Marker for UTF-16 Big Endian
   * (utf-16 and ucs-2).
   *
   * @param bom a buffer.
   * @return true if the buffer has a BOM for UTF-16 Big Endian.
   */
  public static boolean hasUTF16BEBom(@NotNull byte[] bom) {
    return ArrayUtil.startsWith(bom, UTF16BE_BOM);
  }
  public static boolean hasUTF32BEBom(@NotNull byte[] bom) {
    return ArrayUtil.startsWith(bom, UTF32BE_BOM);
  }
  public static boolean hasUTF32LEBom(@NotNull byte[] bom) {
    return ArrayUtil.startsWith(bom, UTF32LE_BOM);
  }

  /**
   * Retrieves all the available {@code Charset}s on the platform,
   * among which the default {@code charset}.
   *
   * @return an array of {@code Charset}s.
   */
  @NotNull
  public static Charset[] getAvailableCharsets() {
    Collection<Charset> collection = Charset.availableCharsets().values();
    return collection.toArray(new Charset[collection.size()]);
  }

  @NotNull
  public static byte[] getUtf8Bytes(@NotNull String s) {
    try {
      return s.getBytes(UTF8);
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException("UTF-8 must be supported", e);
    }
  }

  public static int getBOMLength(@NotNull byte[] content, @NotNull Charset charset) {
    if (charset.name().contains(UTF8) && hasUTF8Bom(content)) {
      return UTF8_BOM.length;
    }
    if (hasUTF32BEBom(content)) {
      return UTF32BE_BOM.length;
    }
    if (hasUTF32BEBom(content)) {
      return UTF32BE_BOM.length;
    }
    if (hasUTF16LEBom(content)) {
      return UTF16LE_BOM.length;
    }
    if (hasUTF16BEBom(content)) {
      return UTF16BE_BOM.length;
    }
    return 0;
  }

  /**
   * @return BOM which is associated with this charset and the charset must have this BOM, or null otherwise.
   *         Currently these are UTF-16xx and UTF-32xx families.
   *         UTF-8, on the other hand, might have BOM {@link #UTF8_BOM} which is optional, thus it will not returned in this method
   */
  @Nullable
  public static byte[] getMandatoryBom(@NotNull Charset charset) {
    return CHARSET_TO_MANDATORY_BOM.get(charset);
  }

  /**
   * @return BOM which can be associated with this charset, or null otherwise.
   *         Currently these are UTF-16xx, UTF-32xx and UTF-8.
   */
  @Nullable
  public static byte[] getPossibleBom(@NotNull Charset charset) {
    if (charset.equals(UTF8_CHARSET)) return UTF8_BOM;
    return CHARSET_TO_MANDATORY_BOM.get(charset);
  }

  // byte sequence for this encoding is allowed to be prepended with this BOM
  public static boolean canHaveBom(@NotNull Charset charset, @NotNull byte[] bom) {
    return charset.equals(UTF8_CHARSET) && Arrays.equals(bom, UTF8_BOM)
           || Arrays.equals(getMandatoryBom(charset), bom);
  }

  @Nullable
  public static Charset forName(@Nullable String name) {
    Charset charset = null;
    if (name != null) {
      try {
        charset = Charset.forName(name);
      }
      catch (IllegalCharsetNameException ignored) {
        //ignore
      }
      catch(UnsupportedCharsetException ignored){
        //ignore
      }
    }

    return charset;
  }
}
