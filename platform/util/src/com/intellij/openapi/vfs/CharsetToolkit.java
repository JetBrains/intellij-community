// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>Utility class to guess the encoding of a given byte array.
 * The guess is unfortunately not 100% sure. Especially for 8-bit charsets.
 * It's not possible to know for sure, which 8-bit charset is used.
 * We will then infer that the charset encountered is the same as the default standard charset.</p>
 *
 * <p>On the other hand, Unicode files encoded in UTF-16 (low or big endian) or UTF-8 files
 * with a Byte Order Marker are easy to find. For UTF-8 files with no BOM, if the buffer
 * is wide enough, it's easy to guess.</p>
 *
 * <p>Tested against a complicated UTF-8 file, Sun's implementation does not render bad UTF-8
 * constructs as expected by the specification. But with buffer wide enough, the method {@link #guessEncoding}
 * did behave correctly and recognized the UTF-8 charset.</p>
 *
 * <p>A byte buffer of 4 KB or 8 KB is sufficient to be able to guess the encoding.</p>
 *
 * <p>Usage:
 * <pre>
 * // guess the encoding
 * Charset guessedCharset = CharsetToolkit.guessEncoding(file, 4096);
 *
 * // create a reader with the charset we've just discovered
 * try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), guessedCharset)) {
 *   //...
 * }
 * </pre>
 * </p>
 *
 * @author Guillaume LAFORGE
 */
public final class CharsetToolkit {
  public static final String UTF8 = "UTF-8";
  /** @deprecated use {@link StandardCharsets#UTF_8} instead */
  @Deprecated
  public static final Charset UTF8_CHARSET = StandardCharsets.UTF_8;
  /** @deprecated use {@link StandardCharsets#UTF_16LE} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final Charset UTF_16LE_CHARSET = StandardCharsets.UTF_16LE;
  /** @deprecated use {@link StandardCharsets#UTF_16BE} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final Charset UTF_16BE_CHARSET = StandardCharsets.UTF_16BE;
  public static final Charset UTF_32BE_CHARSET = Charset.forName("UTF-32BE");
  public static final Charset UTF_32LE_CHARSET = Charset.forName("UTF-32LE");
  public static final Charset WIN_1251_CHARSET = Charset.forName("windows-1251");

  private static final byte FF = (byte)0xff;
  private static final byte FE = (byte)0xfe;
  private static final byte EF = (byte)0xef;
  private static final byte BB = (byte)0xbb;
  private static final byte BF = (byte)0xbf;
  private static final int BINARY_THRESHOLD = 9; // characters with codes below this considered to be binary

  private final byte[] buffer;
  private final @NotNull Charset defaultCharset;
  private final boolean enforce8Bit;

  public static final byte[] UTF8_BOM = {0xffffffef, 0xffffffbb, 0xffffffbf};
  public static final byte[] UTF16LE_BOM = {-1, -2, };
  public static final byte[] UTF16BE_BOM = {-2, -1, };
  private static final byte[] UTF32BE_BOM = {0, 0, -2, -1, };
  private static final byte[] UTF32LE_BOM = {-1, -2, 0, 0 };
  public static final String FILE_ENCODING_PROPERTY = "file.encoding";

  private static final Map<Charset, byte[]> CHARSET_TO_MANDATORY_BOM = new HashMap<>(4);
  static {
    CHARSET_TO_MANDATORY_BOM.put(StandardCharsets.UTF_16LE, UTF16LE_BOM);
    CHARSET_TO_MANDATORY_BOM.put(StandardCharsets.UTF_16BE, UTF16BE_BOM);
    CHARSET_TO_MANDATORY_BOM.put(UTF_32BE_CHARSET, UTF32BE_BOM);
    CHARSET_TO_MANDATORY_BOM.put(UTF_32LE_CHARSET, UTF32LE_BOM);
  }

  /**
   * Constructor of the {@code CharsetToolkit} utility class.
   *  @param buffer the byte buffer of which we want to know the encoding.
   * @param defaultCharset the default Charset to use in case an 8-bit charset is recognized.
   * @param enforce8Bit If US-ASCII is recognized, enforce to return the default encoding, rather than US-ASCII.
   *     It might be a file without any special character in the range 128-255, but that may be or become
   *     a file encoded with the default {@code charset} rather than US-ASCII.
   */
  public CharsetToolkit(byte @NotNull [] buffer, @NotNull Charset defaultCharset, boolean enforce8Bit) {
    this.buffer = buffer;
    this.defaultCharset = defaultCharset;
    this.enforce8Bit = enforce8Bit;
    if (buffer.length == 0){
      throw new IllegalArgumentException("Can't analyze empty buffer");
    }
  }

  public static @NotNull InputStream inputStreamSkippingBOM(@NotNull InputStream stream) throws IOException {
    if (!stream.markSupported()) {
      //noinspection IOResourceOpenedButNotSafelyClosed
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
   * Retrieves the default Charset
   */
  public @NotNull Charset getDefaultCharset() {
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
   * 0000 0000-0000 007F       0.......
   * 0000 0080-0000 07FF       110..... 10......
   * 0000 0800-0000 FFFF       1110.... 10...... 10......
   * 0001 0000-001F FFFF       11110... 10...... 10...... 10......
   * 0020 0000-03FF FFFF       111110.. 10...... 10...... 10...... 10......
   * 0400 0000-7FFF FFFF       1111110. 10...... 10...... 10...... 10...... 10......
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
        // if no byte with a high order bit set, the encoding is US-ASCII
        // (it might have been UTF-7, but this encoding is usually internally used only by mail systems)
        // returns the default charset rather than US-ASCII if the enforce8Bit flag is set.
        return enforce8Bit ? defaultCharset : StandardCharsets.US_ASCII;
      case INVALID_UTF8:
        return defaultCharset;
      case VALID_UTF8:
        return StandardCharsets.UTF_8;
      case BINARY:
      default:
        break;
    }
    return null;
  }

  public static @NotNull String bytesToString(byte @NotNull [] bytes, final @NotNull Charset defaultCharset) {
    if (bytes.length == 0) return "";
    Charset charset = new CharsetToolkit(bytes, defaultCharset, false).guessEncoding(bytes.length);
    if (charset == null) charset = defaultCharset; // binary content. This is silly but method contract says to return something anyway
    return decodeString(bytes, charset);
  }

  public static @NotNull String decodeString(byte @NotNull [] bytes, final @NotNull Charset charset) {
    int bomLength = getBOMLength(bytes, charset);
    final CharBuffer charBuffer = charset.decode(ByteBuffer.wrap(bytes, bomLength, bytes.length - bomLength));
    return charBuffer.toString();
  }

  public static @Nullable String tryDecodeString(byte @NotNull [] bytes, final @NotNull Charset charset) {
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
    SEVEN_BIT,     // ASCII characters only
    VALID_UTF8,    // UTF-8
    INVALID_UTF8,  // invalid UTF: illegal utf-8 continuation sequences were found
    BINARY         // binary: char with code < BINARY_THRESHOLD(9) was found
  }

  public @NotNull GuessedEncoding guessFromContent(int guess_length) {
    return guessFromContent(0, guess_length);
  }

  public @NotNull GuessedEncoding guessFromContent(int startOffset, int endOffset) {
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
        break;
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

  public @Nullable Charset guessFromBOM() {
    return guessFromBOM(buffer);
  }

  public static @Nullable Charset guessFromBOM(byte @NotNull [] buffer) {
    if (hasUTF8Bom(buffer)) return StandardCharsets.UTF_8;
    if (hasUTF32BEBom(buffer)) return UTF_32BE_CHARSET;
    if (hasUTF32LEBom(buffer)) return UTF_32LE_CHARSET;
    if (hasUTF16LEBom(buffer)) return StandardCharsets.UTF_16LE;
    if (hasUTF16BEBom(buffer)) return StandardCharsets.UTF_16BE;

    return null;
  }

  public Charset guessEncoding(int guess_length) {
    return guessEncoding(0,guess_length, defaultCharset);
  }

  public static Charset guessEncoding(@NotNull File f, int bufferLength, @NotNull Charset defaultCharset) throws IOException {
    if (bufferLength == 0) {
      throw new IllegalArgumentException("Can't analyze empty buffer");
    }
    byte[] buffer = new byte[bufferLength];
    int read;
    try (FileInputStream fis = new FileInputStream(f)) {
      read = fis.read(buffer);
    }
    CharsetToolkit toolkit = new CharsetToolkit(buffer, defaultCharset, false);
    return toolkit.guessEncoding(read);
  }

  /**
   * If the byte has the form 10xxxxx, then it's a continuation byte of a multiple byte character;
   */
  private static boolean isContinuationChar(byte b) {
    return b <= -65;
  }

  /**
   * If the byte has the form 110xxxx, then it's the first byte of a two-bytes sequence character.
   */
  private static boolean isTwoBytesSequence(byte b) {
    return -64 <= b && b <= -33;
  }

  /**
   * If the byte has the form 1110xxx, then it's the first byte of a three-bytes sequence character.
   */
  private static boolean isThreeBytesSequence(byte b) {
    return -32 <= b && b <= -17;
  }

  /**
   * If the byte has the form 11110xx, then it's the first byte of a four-bytes sequence character.
   */
  private static boolean isFourBytesSequence(byte b) {
    return -16 <= b && b <= -9;
  }

  /**
   * If the byte has the form 11110xx, then it's the first byte of a five-bytes sequence character.
   */
  private static boolean isFiveBytesSequence(byte b) {
    return -8 <= b && b <= -5;
  }

  /**
   * If the byte has the form 1110xxx, then it's the first byte of a six-bytes sequence character.
   */
  private static boolean isSixBytesSequence(byte b) {
    return -4 <= b && b <= -3;
  }

  /**
   * Retrieve the default charset of the system.
   */
  public static @NotNull Charset getDefaultSystemCharset() {
    return Charset.defaultCharset();
  }

  /**
   * Retrieve the platform charset of the system (determined by "sun.jnu.encoding" property)
   */
  public static @NotNull Charset getPlatformCharset() {
    String name = System.getProperty("sun.jnu.encoding");
    Charset value = forName(name);
    return value == null ? getDefaultSystemCharset() : value;
  }

  /**
   * Has a Byte Order Marker for UTF-8 (Used by Microsoft's Notepad and other editors).
   */
  public static boolean hasUTF8Bom(byte @NotNull [] bom) {
    return ArrayUtil.startsWith(bom, UTF8_BOM);
  }

  /**
   * Has a Byte Order Marker for UTF-16 Low Endian (ucs-2le, ucs-4le, and ucs-16le).
   */
  public static boolean hasUTF16LEBom(byte @NotNull [] bom) {
    return ArrayUtil.startsWith(bom, UTF16LE_BOM);
  }

  /**
   * Has a Byte Order Marker for UTF-16 Big Endian (utf-16 and ucs-2).
   */
  public static boolean hasUTF16BEBom(byte @NotNull [] bom) {
    return ArrayUtil.startsWith(bom, UTF16BE_BOM);
  }
  public static boolean hasUTF32BEBom(byte @NotNull [] bom) {
    return ArrayUtil.startsWith(bom, UTF32BE_BOM);
  }
  public static boolean hasUTF32LEBom(byte @NotNull [] bom) {
    return ArrayUtil.startsWith(bom, UTF32LE_BOM);
  }

  /**
   * Retrieves all the available {@code Charset}s on the platform, among which the default {@code charset}.
   */
  public static Charset @NotNull [] getAvailableCharsets() {
    Collection<Charset> collection = Charset.availableCharsets().values();
    return collection.toArray(new Charset[0]);
  }

  public static int getBOMLength(byte @NotNull [] content, @NotNull Charset charset) {
    if (charset.name().contains(UTF8) && hasUTF8Bom(content)) {
      return UTF8_BOM.length;
    }
    if (hasUTF32BEBom(content)) {
      return UTF32BE_BOM.length;
    }
    if (hasUTF32LEBom(content)) {
      return UTF32LE_BOM.length;
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
   *         Currently, these are UTF-16xx and UTF-32xx families.
   *         UTF-8, on the other hand, might have BOM {@link #UTF8_BOM} which is optional, thus it won't be returned in this method.
   */
  public static byte @Nullable [] getMandatoryBom(@NotNull Charset charset) {
    return CHARSET_TO_MANDATORY_BOM.get(charset);
  }

  /**
   * @return BOM which can be associated with this charset, or null otherwise.
   *         Currently, these are UTF-16xx, UTF-32xx and UTF-8.
   */
  public static byte @Nullable [] getPossibleBom(@NotNull Charset charset) {
    return charset.equals(StandardCharsets.UTF_8) ? UTF8_BOM : CHARSET_TO_MANDATORY_BOM.get(charset);
  }

  // byte sequence for this encoding is allowed to be prepended with this BOM
  public static boolean canHaveBom(@NotNull Charset charset, byte @NotNull [] bom) {
    return charset.equals(StandardCharsets.UTF_8) && Arrays.equals(bom, UTF8_BOM) || Arrays.equals(getMandatoryBom(charset), bom);
  }

  public static @Nullable Charset forName(@Nullable String name) {
    Charset charset = null;
    if (name != null) {
      try {
        charset = Charset.forName(name);
      }
      catch (IllegalCharsetNameException | UnsupportedCharsetException ignored) { }
    }

    return charset;
  }
}
