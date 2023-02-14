// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io;

import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;

public final class JsonReaderEx implements Closeable {
  /** The only non-execute prefix this parser permits */
  private static final char[] NON_EXECUTE_PREFIX = ")]}'\n".toCharArray();
  private static final long MIN_INCOMPLETE_INTEGER = Long.MIN_VALUE / 10;

  private static final int PEEKED_NONE = 0;
  private static final int PEEKED_BEGIN_OBJECT = 1;
  private static final int PEEKED_END_OBJECT = 2;
  private static final int PEEKED_BEGIN_ARRAY = 3;
  private static final int PEEKED_END_ARRAY = 4;
  private static final int PEEKED_TRUE = 5;
  private static final int PEEKED_FALSE = 6;
  private static final int PEEKED_NULL = 7;
  private static final int PEEKED_SINGLE_QUOTED = 8;
  private static final int PEEKED_DOUBLE_QUOTED = 9;
  private static final int PEEKED_UNQUOTED = 10;
  /** When this is returned, the string value is stored in peekedString. */
  private static final int PEEKED_BUFFERED = 11;
  private static final int PEEKED_SINGLE_QUOTED_NAME = 12;
  private static final int PEEKED_DOUBLE_QUOTED_NAME = 13;
  private static final int PEEKED_UNQUOTED_NAME = 14;
  /** When this is returned, the integer value is stored in peekedLong. */
  private static final int PEEKED_LONG = 15;
  private static final int PEEKED_NUMBER = 16;
  private static final int PEEKED_EOF = 17;

  /* State machine when parsing numbers */
  private static final int NUMBER_CHAR_NONE = 0;
  private static final int NUMBER_CHAR_SIGN = 1;
  private static final int NUMBER_CHAR_DIGIT = 2;
  private static final int NUMBER_CHAR_DECIMAL = 3;
  private static final int NUMBER_CHAR_FRACTION_DIGIT = 4;
  private static final int NUMBER_CHAR_EXP_E = 5;
  private static final int NUMBER_CHAR_EXP_SIGN = 6;
  private static final int NUMBER_CHAR_EXP_DIGIT = 7;

  private final CharSequence sourceSequence;

  /** True to accept non-spec compliant JSON */
  private boolean lenient = false;

  private int position;
  private final int limit;

  private int peeked = PEEKED_NONE;

  /**
   * A peeked value that was composed entirely of digits with an optional
   * leading dash. Positive values may not have a leading 0.
   */
  private long peekedLong;

  /**
   * The number of characters in a peeked number literal. Increment 'pos' by
   * this after reading a number.
   */
  private int peekedNumberLength;

  /**
   * A peeked string that should be parsed on the next double, long or string.
   * This is populated before a numeric value is parsed and used if that parsing
   * fails.
   */
  private String peekedString;

  /*
   * The nesting stack. Using a manual array rather than an ArrayList saves 20%.
   */
  private int[] stack;
  private int stackSize = 0;

  private StringBuilder builder;

  /**
   * Creates a new instance that reads a JSON-encoded stream from {@code in}.
   */
  public JsonReaderEx(@NotNull CharSequence sourceSequence) {
    this(sourceSequence, 0);
  }

  public JsonReaderEx(@NotNull CharSequence sourceSequence, int start) {
    this(sourceSequence, start, new int[32]);

    stack[stackSize++] = JsonScope.EMPTY_DOCUMENT;
  }

  private JsonReaderEx(@NotNull CharSequence sourceSequence, int start, int @NotNull [] stack) {
    this.sourceSequence = sourceSequence;
    position = start;
    limit = sourceSequence.length();
    this.stack = stack;
  }

  private final static class JsonScope {
    /**
     * An array with no elements requires no separators or newlines before
     * it is closed.
     */
    static final int EMPTY_ARRAY = 1;

    /**
     * A array with at least one value requires a comma and newline before
     * the next element.
     */
    static final int NONEMPTY_ARRAY = 2;

    /**
     * An object with no name/value pairs requires no separators or newlines
     * before it is closed.
     */
    static final int EMPTY_OBJECT = 3;

    /**
     * An object whose most recent element is a key. The next element must
     * be a value.
     */
    static final int DANGLING_NAME = 4;

    /**
     * An object with at least one name/value pair requires a comma and
     * newline before the next element.
     */
    static final int NONEMPTY_OBJECT = 5;

    /**
     * No object or array has been started.
     */
    static final int EMPTY_DOCUMENT = 6;

    /**
     * A document with at an array or object.
     */
    static final int NONEMPTY_DOCUMENT = 7;

    /**
     * A document that's been closed and cannot be accessed.
     */
    static final int CLOSED = 8;
  }

  /**
   * @return initial char sequence to be parsed
   */
  public CharSequence getSourceSequence() { return sourceSequence; }

  @Nullable
  public JsonReaderEx subReader() {
    JsonToken nextToken = peek();
    switch (nextToken) {
      case BEGIN_ARRAY:
      case BEGIN_OBJECT:
      case STRING:
      case NUMBER:
      case BOOLEAN:
        break;
      case NULL:
        // just return null
        return null;
      default:
        throw createParseError("Cannot create sub reader, next token " + nextToken + " is not value");
    }

    JsonReaderEx subReader = new JsonReaderEx(sourceSequence, position, stack.clone());
    subReader.stackSize = stackSize;
    subReader.peeked = peeked;
    subReader.peekedLong = peekedLong;
    subReader.peekedNumberLength = peekedNumberLength;
    subReader.peekedString = peekedString;
    return subReader;
  }

  @Nullable
  public JsonReaderEx createSubReaderAndSkipValue() {
    JsonReaderEx subReader = subReader();
    skipValue();
    return subReader;
  }

  public void setLenient(boolean lenient) {
    this.lenient = lenient;
  }

  public JsonReaderEx lenient(boolean lenient) {
    this.lenient = lenient;
    return this;
  }

  @SuppressWarnings("UnusedDeclaration")
  public boolean isLenient() {
    return lenient;
  }

  /**
   * Consumes the next token from the JSON stream and asserts that it is the
   * beginning of a new array.
   */
  public void beginArray() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_BEGIN_ARRAY) {
      push(JsonScope.EMPTY_ARRAY);
      peeked = PEEKED_NONE;
    }
    else {
      throw createParseError("Expected BEGIN_ARRAY but was " + peek());
    }
  }

  /**
   * Consumes the next token from the JSON stream and asserts that it is the
   * end of the current array.
   */
  public void endArray() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_END_ARRAY) {
      stackSize--;
      peeked = PEEKED_NONE;
    }
    else {
      throw createParseError("Expected END_ARRAY but was " + peek());
    }
  }

  /**
   * Consumes the next token from the JSON stream and asserts that it is the
   * beginning of a new object.
   */
  public JsonReaderEx beginObject() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_BEGIN_OBJECT) {
      push(JsonScope.EMPTY_OBJECT);
      peeked = PEEKED_NONE;
    }
    else {
      throw createParseError("Expected BEGIN_OBJECT but was " + peek());
    }
    return this;
  }

  /**
   * Consumes the next token from the JSON stream and asserts that it is the
   * end of the current object.
   */
  public void endObject() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_END_OBJECT) {
      stackSize--;
      peeked = PEEKED_NONE;
    }
    else {
      throw new IllegalStateException("Expected END_OBJECT but was " + peek());
    }
  }

  /**
   * Returns true if the current array or object has another element.
   */
  public boolean hasNext() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    return p != PEEKED_END_OBJECT && p != PEEKED_END_ARRAY;
  }

  /**
   * Returns the type of the next token without consuming it.
   */
  public JsonToken peek() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }

    return switch (p) {
      case PEEKED_BEGIN_OBJECT -> JsonToken.BEGIN_OBJECT;
      case PEEKED_END_OBJECT -> JsonToken.END_OBJECT;
      case PEEKED_BEGIN_ARRAY -> JsonToken.BEGIN_ARRAY;
      case PEEKED_END_ARRAY -> JsonToken.END_ARRAY;
      case PEEKED_SINGLE_QUOTED_NAME, PEEKED_DOUBLE_QUOTED_NAME, PEEKED_UNQUOTED_NAME -> JsonToken.NAME;
      case PEEKED_TRUE, PEEKED_FALSE -> JsonToken.BOOLEAN;
      case PEEKED_NULL -> JsonToken.NULL;
      case PEEKED_SINGLE_QUOTED, PEEKED_DOUBLE_QUOTED, PEEKED_UNQUOTED, PEEKED_BUFFERED -> JsonToken.STRING;
      case PEEKED_LONG, PEEKED_NUMBER -> JsonToken.NUMBER;
      case PEEKED_EOF -> JsonToken.END_DOCUMENT;
      default -> throw new AssertionError();
    };
  }

  private int doPeek() {
    int peekStack = stack[stackSize - 1];
    if (peekStack == JsonScope.EMPTY_ARRAY) {
      stack[stackSize - 1] = JsonScope.NONEMPTY_ARRAY;
    }
    else if (peekStack == JsonScope.NONEMPTY_ARRAY) {
      // Look for a comma before the next element.
      int c = nextNonWhitespace(true);
      switch (c) {
        case ']':
          return peeked = PEEKED_END_ARRAY;
        case ';':
          checkLenient(); // fall-through
        case ',':
          break;
        default:
          throw createParseError("Unterminated array");
      }
    }
    else if (peekStack == JsonScope.EMPTY_OBJECT || peekStack == JsonScope.NONEMPTY_OBJECT) {
      stack[stackSize - 1] = JsonScope.DANGLING_NAME;
      // Look for a comma before the next element.
      if (peekStack == JsonScope.NONEMPTY_OBJECT) {
        int c = nextNonWhitespace(true);
        switch (c) {
          case '}':
            return peeked = PEEKED_END_OBJECT;
          case ';':
            checkLenient(); // fall-through
          case ',':
            break;
          default:
            throw createParseError("Unterminated object");
        }
      }
      int c = nextNonWhitespace(true);
      return peeked = switch (c) {
        case '"' -> PEEKED_DOUBLE_QUOTED_NAME;
        case '\'' -> {
          checkLenient();
          yield PEEKED_SINGLE_QUOTED_NAME;
        }
        case '}' -> {
          if (peekStack != JsonScope.NONEMPTY_OBJECT) {
            yield PEEKED_END_OBJECT;
          }
          else {
            throw createParseError("Expected name");
          }
        }
        default -> {
          checkLenient();
          position--; // Don't consume the first character in an unquoted string.
          if (isLiteral((char)c)) {
            yield PEEKED_UNQUOTED_NAME;
          }
          else {
            throw createParseError("Expected name");
          }
        }
      };
    }
    else if (peekStack == JsonScope.DANGLING_NAME) {
      stack[stackSize - 1] = JsonScope.NONEMPTY_OBJECT;
      // Look for a colon before the value.
      int c = nextNonWhitespace(true);
      switch (c) {
        case ':':
          break;
        case '=':
          checkLenient();
          if (position < limit && sourceSequence.charAt(position) == '>') {
            position++;
          }
          break;
        default:
          throw createParseError("Expected ':'");
      }
    }
    else if (peekStack == JsonScope.EMPTY_DOCUMENT) {
      if (lenient) {
        consumeNonExecutePrefix();
      }
      stack[stackSize - 1] = JsonScope.NONEMPTY_DOCUMENT;
    }
    else if (peekStack == JsonScope.NONEMPTY_DOCUMENT) {
      int c = nextNonWhitespace(false);
      if (c == -1) {
        return peeked = PEEKED_EOF;
      }
      else {
        checkLenient();
        position--;
      }
    }
    else if (peekStack == JsonScope.CLOSED) {
      throw new IllegalStateException("JsonReader is closed");
    }

    int c = nextNonWhitespace(true);
    switch (c) {
      case ']':
        if (peekStack == JsonScope.EMPTY_ARRAY) {
          return peeked = PEEKED_END_ARRAY;
        }
        // fall-through to handle ",]"
      case ';':
      case ',':
        // In lenient mode, a 0-length literal in an array means 'null'.
        if (peekStack == JsonScope.EMPTY_ARRAY || peekStack == JsonScope.NONEMPTY_ARRAY) {
          checkLenient();
          position--;
          return peeked = PEEKED_NULL;
        }
        else {
          throw createParseError("Unexpected value");
        }
      case '\'':
        checkLenient();
        return peeked = PEEKED_SINGLE_QUOTED;
      case '"':
        if (stackSize == 1) {
          checkLenient();
        }
        return peeked = PEEKED_DOUBLE_QUOTED;
      case '[':
        return peeked = PEEKED_BEGIN_ARRAY;
      case '{':
        return peeked = PEEKED_BEGIN_OBJECT;
      default:
        position--; // Don't consume the first character in a literal value.
    }

    if (stackSize == 1) {
      checkLenient(); // Top-level value isn't an array or an object.
    }

    int result = peekKeyword();
    if (result != PEEKED_NONE) {
      return result;
    }

    result = peekNumber();
    if (result != PEEKED_NONE) {
      return result;
    }

    if (!isLiteral(sourceSequence.charAt(position))) {
      throw createParseError("Expected value");
    }

    checkLenient();
    return peeked = PEEKED_UNQUOTED;
  }

  private int peekKeyword() {
    // Figure out which keyword we're matching against by its first character.
    char c = sourceSequence.charAt(position);
    String keyword;
    String keywordUpper;
    int peeking;
    if (c == 't' || c == 'T') {
      keyword = "true";
      keywordUpper = "TRUE";
      peeking = PEEKED_TRUE;
    }
    else if (c == 'f' || c == 'F') {
      keyword = "false";
      keywordUpper = "FALSE";
      peeking = PEEKED_FALSE;
    }
    else if (c == 'n' || c == 'N') {
      keyword = "null";
      keywordUpper = "NULL";
      peeking = PEEKED_NULL;
    }
    else {
      return PEEKED_NONE;
    }

    // Confirm that chars [1..length) match the keyword.
    int length = keyword.length();
    for (int i = 1; i < length; i++) {
      if (position + i >= limit) {
        return PEEKED_NONE;
      }
      c = sourceSequence.charAt(position + i);
      if (c != keyword.charAt(i) && c != keywordUpper.charAt(i)) {
        return PEEKED_NONE;
      }
    }

    if ((position + length < limit) && isLiteral(sourceSequence.charAt(position + length))) {
      return PEEKED_NONE; // Don't match trues, falsey or nullsoft!
    }

    // We've found the keyword followed either by EOF or by a non-literal character.
    position += length;
    return peeked = peeking;
  }

  private int peekNumber() {
    // Like nextNonWhitespace, this uses locals 'p' and 'l' to save inner-loop field access.
    CharSequence in = this.sourceSequence;
    int p = position;
    int l = limit;

    long value = 0; // Negative to accommodate Long.MIN_VALUE more easily.
    boolean negative = false;
    boolean fitsInLong = true;
    int last = NUMBER_CHAR_NONE;

    int i = 0;

    charactersOfNumber:
    for (; true; i++) {
      if (p + i == l) {
        if (i == limit) {
          // Though this looks like a well-formed number, it's too long to continue reading. Give up
          // and let the application handle this as an unquoted literal.
          return PEEKED_NONE;
        }
        p = position;
        l = limit;
      }

      char c = in.charAt(p + i);
      switch (c) {
        case '-' -> {
          if (last == NUMBER_CHAR_NONE) {
            negative = true;
            last = NUMBER_CHAR_SIGN;
            continue;
          }
          else if (last == NUMBER_CHAR_EXP_E) {
            last = NUMBER_CHAR_EXP_SIGN;
            continue;
          }
          return PEEKED_NONE;
        }
        case '+' -> {
          if (last == NUMBER_CHAR_EXP_E) {
            last = NUMBER_CHAR_EXP_SIGN;
            continue;
          }
          return PEEKED_NONE;
        }
        case 'e', 'E' -> {
          if (last == NUMBER_CHAR_DIGIT || last == NUMBER_CHAR_FRACTION_DIGIT) {
            last = NUMBER_CHAR_EXP_E;
            continue;
          }
          return PEEKED_NONE;
        }
        case '.' -> {
          if (last == NUMBER_CHAR_DIGIT) {
            last = NUMBER_CHAR_DECIMAL;
            continue;
          }
          return PEEKED_NONE;
        }
        default -> {
          if (c < '0' || c > '9') {
            if (!isLiteral(c)) {
              break charactersOfNumber;
            }
            return PEEKED_NONE;
          }
          if (last == NUMBER_CHAR_SIGN || last == NUMBER_CHAR_NONE) {
            value = -(c - '0');
            last = NUMBER_CHAR_DIGIT;
          }
          else if (last == NUMBER_CHAR_DIGIT) {
            if (value == 0) {
              return PEEKED_NONE; // Leading '0' prefix is not allowed (since it could be octal).
            }
            long newValue = value * 10 - (c - '0');
            fitsInLong &= value > MIN_INCOMPLETE_INTEGER
                          || (value == MIN_INCOMPLETE_INTEGER && newValue < value);
            value = newValue;
          }
          else if (last == NUMBER_CHAR_DECIMAL) {
            last = NUMBER_CHAR_FRACTION_DIGIT;
          }
          else if (last == NUMBER_CHAR_EXP_E || last == NUMBER_CHAR_EXP_SIGN) {
            last = NUMBER_CHAR_EXP_DIGIT;
          }
        }
      }
    }

    // We've read a complete number. Decide if it's a PEEKED_LONG or a PEEKED_NUMBER.
    if (last == NUMBER_CHAR_DIGIT && fitsInLong && (value != Long.MIN_VALUE || negative)) {
      peekedLong = negative ? value : -value;
      position += i;
      return peeked = PEEKED_LONG;
    }
    else if (last == NUMBER_CHAR_DIGIT || last == NUMBER_CHAR_FRACTION_DIGIT || last == NUMBER_CHAR_EXP_DIGIT) {
      peekedNumberLength = i;
      return peeked = PEEKED_NUMBER;
    }
    else {
      return PEEKED_NONE;
    }
  }

  /**
   * Consumes the non-execute prefix if it exists.
   */
  private void consumeNonExecutePrefix() {
    // fast forward through the leading whitespace
    nextNonWhitespace(true);
    position--;

    if (position + NON_EXECUTE_PREFIX.length > limit) {
      return;
    }

    for (int i = 0; i < NON_EXECUTE_PREFIX.length; i++) {
      if (sourceSequence.charAt(position + i) != NON_EXECUTE_PREFIX[i]) {
        return; // not a security token!
      }
    }

    // we consumed a security token!
    position += NON_EXECUTE_PREFIX.length;
  }

  private boolean isLiteral(char c) {
    switch (c) {
      case '/':
      case '\\':
      case ';':
      case '#':
      case '=':
        checkLenient(); // fall-through
      case '{':
      case '}':
      case '[':
      case ']':
      case ':':
      case ',':
      case ' ':
      case '\t':
      case '\f':
      case '\r':
      case '\n':
        return false;
      default:
        return true;
    }
  }

  /**
   * Returns the next token, a {@link JsonToken#NAME property name}, and consumes it
   */
  public String nextName() {
    String result = nextNameOrNull();
    if (result == null) {
      throw createParseError("Expected a name but was " + peek());
    }
    return result;
  }

  @Nullable
  public String nextNameOrNull() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    String result;
    if (p == PEEKED_UNQUOTED_NAME) {
      result = nextUnquotedValue();
    }
    else if (p == PEEKED_SINGLE_QUOTED_NAME) {
      result = nextQuotedValue('\'');
    }
    else if (p == PEEKED_DOUBLE_QUOTED_NAME) {
      result = nextQuotedValue('"');
    }
    else {
      if (p != PEEKED_END_OBJECT && p != PEEKED_END_ARRAY) {
        throw createParseError("Expected a name but was " + peek());
      }
      return null;
    }
    peeked = PEEKED_NONE;
    return result;
  }

  public CharSequence nextNameAsCharSequence() {
    // todo
    return nextName();
  }

  public String nextAsString() {
    return peek() == JsonToken.STRING ? nextString() : null;
  }

  public String nextNullableString() {
    if (peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    else {
      return nextString();
    }
  }

  public String nextString() {
    return nextString(false);
  }

  /**
   * Returns the {@link JsonToken#STRING string} value of the next token,
   * consuming it. If the next token is a number, this method will return its
   * string form.
   *
   * @throws IllegalStateException if the next token is not a string or if
   *     this reader is closed.
   */
  public String nextString(boolean anyPrimitiveAsString) {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    String result;
    if (p == PEEKED_UNQUOTED) {
      result = nextUnquotedValue();
    }
    else if (p == PEEKED_SINGLE_QUOTED) {
      result = nextQuotedValue('\'');
    }
    else if (p == PEEKED_DOUBLE_QUOTED) {
      result = nextQuotedValue('"');
    }
    else if (p == PEEKED_BUFFERED) {
      result = peekedString;
      peekedString = null;
    }
    else if (p == PEEKED_LONG) {
      result = Long.toString(peekedLong);
    }
    else if (p == PEEKED_NUMBER) {
      int end = position + peekedNumberLength;
      result = sourceSequence.subSequence(position, end).toString();
      position = end;
    }
    else if (anyPrimitiveAsString && p == PEEKED_TRUE) {
      result = "true";
    }
    else if (anyPrimitiveAsString && p == PEEKED_FALSE) {
      result = "false";
    }
    else if (anyPrimitiveAsString && p == PEEKED_NULL) {
      result = "null";
    }
    else {
      throw createParseError("Expected a string but was " + peek());
    }
    peeked = PEEKED_NONE;
    return result;
  }

  /**
   * Returns the {@link JsonToken#BOOLEAN boolean} value of the next token,
   * consuming it.
   *
   * @throws IllegalStateException if the next token is not a boolean or if
   *     this reader is closed.
   */
  public boolean nextBoolean() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_TRUE) {
      peeked = PEEKED_NONE;
      return true;
    }
    else if (p == PEEKED_FALSE) {
      peeked = PEEKED_NONE;
      return false;
    }
    throw createParseError("Expected a boolean but was " + peek());
  }

  /**
   * Consumes the next token from the JSON stream and asserts that it is a
   * literal null.
   *
   * @throws IllegalStateException if the next token is not null or if this
   *     reader is closed.
   */
  public void nextNull() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_NULL) {
      peeked = PEEKED_NONE;
    }
    else {
      throw createParseError("Expected null but was " + peek());
    }
  }

  /**
   * Returns the {@link JsonToken#NUMBER double} value of the next token,
   * consuming it. If the next token is a string, this method will attempt to
   * parse it as a double using {@link Double#parseDouble(String)}.
   *
   * @throws IllegalStateException if the next token is not a literal value.
   * @throws NumberFormatException if the next literal value cannot be parsed
   *     as a double, or is non-finite.
   */
  public double nextDouble() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }

    if (p == PEEKED_LONG) {
      peeked = PEEKED_NONE;
      return (double)peekedLong;
    }

    if (p == PEEKED_NUMBER) {
      int end = position + peekedNumberLength;
      peekedString = sourceSequence.subSequence(position, end).toString();
      position = end;
    }
    else if (p == PEEKED_SINGLE_QUOTED || p == PEEKED_DOUBLE_QUOTED) {
      peekedString = nextQuotedValue(p == PEEKED_SINGLE_QUOTED ? '\'' : '"');
    }
    else if (p == PEEKED_UNQUOTED) {
      peekedString = nextUnquotedValue();
    }
    else if (p != PEEKED_BUFFERED) {
      throw createParseError("Expected a double but was " + peek());
    }

    peeked = PEEKED_BUFFERED;
    double result = Double.parseDouble(peekedString); // don't catch this NumberFormatException.
    if (!lenient && (Double.isNaN(result) || Double.isInfinite(result))) {
      throw createParseError("JSON forbids NaN and infinities: " + result);
    }
    peekedString = null;
    peeked = PEEKED_NONE;
    return result;
  }

  /**
   * Returns the {@link JsonToken#NUMBER long} value of the next token,
   * consuming it. If the next token is a string, this method will attempt to
   * parse it as a long. If the next token's numeric value cannot be exactly
   * represented by a Java {@code long}, this method throws.
   *
   * @throws IllegalStateException if the next token is not a literal value.
   * @throws NumberFormatException if the next literal value cannot be parsed
   *     as a number, or exactly represented as a long.
   */
  public long nextLong() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }

    if (p == PEEKED_LONG) {
      peeked = PEEKED_NONE;
      return peekedLong;
    }

    if (p == PEEKED_NUMBER) {
      int end = position + peekedNumberLength;
      peekedString = sourceSequence.subSequence(position, end).toString();
      position = end;
    }
    else if (p == PEEKED_SINGLE_QUOTED || p == PEEKED_DOUBLE_QUOTED) {
      peekedString = nextQuotedValue(p == PEEKED_SINGLE_QUOTED ? '\'' : '"');
      try {
        long result = Long.parseLong(peekedString);
        peeked = PEEKED_NONE;
        return result;
      }
      catch (NumberFormatException ignored) {
        // Fall back to parse as a double below.
      }
    }
    else {
      throw createParseError("Expected a long but was " + peek());
    }

    peeked = PEEKED_BUFFERED;
    double asDouble = Double.parseDouble(peekedString); // don't catch this NumberFormatException.
    long result = (long)asDouble;
    if (result != asDouble) { // Make sure no precision was lost casting to 'long'.
      throw new NumberFormatException("Expected a long but was " + peekedString
                                      + " at line " + getLineNumber() + " column " + getColumnNumber());
    }
    peekedString = null;
    peeked = PEEKED_NONE;
    return result;
  }

  /**
   * Returns the string up to but not including {@code quote}, unescaping any
   * character escape sequences encountered along the way. The opening quote
   * should have already been read. This consumes the closing quote, but does
   * not include it in the returned string.
   *
   * @param quote either ' or ".
   * @throws NumberFormatException if any unicode escape sequences are
   *     malformed.
   */
  private String nextQuotedValue(char quote) {
    // Like nextNonWhitespace, this uses locals 'p' and 'l' to save inner-loop field access.
    CharSequence in = this.sourceSequence;

    int p = position;
    int l = limit;
    // the index of the first character not yet appended to the builder
    int start = p;
    StringBuilder builder = null;
    while (p < l) {
      char c = in.charAt(p++);
      if (c == quote) {
        position = p;
        if (builder == null) {
          return in.subSequence(start, p - 1).toString();
        }
        else {
          return builder.append(in, start, p - 1).toString();
        }
      }
      else if (c == '\\') {
        position = p;
        if (builder == null) {
          if (this.builder == null) {
            this.builder = new StringBuilder((p - start) + 16);
          }
          else {
            this.builder.setLength(0);
          }
          builder = this.builder;
        }
        builder.append(in, start, p - 1);
        builder.append(readEscapeCharacter());
        p = position;
        l = limit;
        start = p;
      }
      //else if (c == '\n') {
      //  //lineNumber++;
      //  //lineStart = p;
      //}
    }
    position = p;
    throw createParseError("Unterminated string");
  }

  /**
   * Returns an unquoted value as a string.
   */
  private String nextUnquotedValue() {
    int i = position;
    findNonLiteralCharacter:
    for (; i < limit; i++) {
      switch (sourceSequence.charAt(i)) {
        case '/':
        case '\\':
        case ';':
        case '#':
        case '=':
          checkLenient(); // fall-through
        case '{':
        case '}':
        case '[':
        case ']':
        case ':':
        case ',':
        case ' ':
        case '\t':
        case '\f':
        case '\r':
        case '\n':
          break findNonLiteralCharacter;
      }
    }

    String result = sourceSequence.subSequence(position, i).toString();
    position = i;
    return result;
  }

  private void skipQuotedValue(char quote) {
    // Like nextNonWhitespace, this uses locals 'p' and 'l' to save inner-loop field access.
    CharSequence in = this.sourceSequence;
    int p = position;
    int l = limit;
    // the index of the first character not yet appended to the builder
    while (p < l) {
      int c = in.charAt(p++);
      if (c == quote) {
        position = p;
        return;
      }
      else if (c == '\\') {
        position = p;
        readEscapeCharacter();
        p = position;
        l = limit;
      }
      //else if (c == '\n') {
      //  //lineNumber++;
      //  //lineStart = p;
      //}
    }
    position = p;
    throw createParseError("Unterminated string");
  }

  private void skipUnquotedValue() {
    int i = position;
    for (; i < limit; i++) {
      switch (sourceSequence.charAt(i)) {
        case '/':
        case '\\':
        case ';':
        case '#':
        case '=':
          checkLenient(); // fall-through
        case '{':
        case '}':
        case '[':
        case ']':
        case ':':
        case ',':
        case ' ':
        case '\t':
        case '\f':
        case '\r':
        case '\n':
          position = i;
          return;
      }
    }
    position = i;
  }

  /**
   * Returns the {@link JsonToken#NUMBER int} value of the next token,
   * consuming it. If the next token is a string, this method will attempt to
   * parse it as an int. If the next token's numeric value cannot be exactly
   * represented by a Java {@code int}, this method throws.
   *
   * @throws IllegalStateException if the next token is not a literal value.
   * @throws NumberFormatException if the next literal value cannot be parsed
   *     as a number, or exactly represented as an int.
   */
  public int nextInt() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }

    int result;
    if (p == PEEKED_LONG) {
      result = (int)peekedLong;
      if (peekedLong != result) { // Make sure no precision was lost casting to 'int'.
        throw new NumberFormatException("Expected an int but was " + peekedLong
                                        + " at line " + getLineNumber() + " column " + getColumnNumber());
      }
      peeked = PEEKED_NONE;
      return result;
    }

    if (p == PEEKED_NUMBER) {
      int end = position + peekedNumberLength;
      peekedString = sourceSequence.subSequence(position, end).toString();
      position = end;
    }
    else if (p == PEEKED_SINGLE_QUOTED || p == PEEKED_DOUBLE_QUOTED) {
      peekedString = nextQuotedValue(p == PEEKED_SINGLE_QUOTED ? '\'' : '"');
      try {
        result = Integer.parseInt(peekedString);
        peeked = PEEKED_NONE;
        return result;
      }
      catch (NumberFormatException ignored) {
        // Fall back to parse as a double below.
      }
    }
    else {
      throw createParseError("Expected an int but was " + peek());
    }

    peeked = PEEKED_BUFFERED;
    double asDouble = Double.parseDouble(peekedString); // don't catch this NumberFormatException.
    result = (int)asDouble;
    if (result != asDouble) { // Make sure no precision was lost casting to 'int'.
      throw new NumberFormatException("Expected an int but was " + peekedString
                                      + " at line " + getLineNumber() + " column " + getColumnNumber());
    }
    peekedString = null;
    peeked = PEEKED_NONE;
    return result;
  }

  /**
   * Closes this JSON reader and the underlying {@link java.io.Reader}.
   */
  @Override
  public void close() {
    peeked = PEEKED_NONE;
    stack[0] = JsonScope.CLOSED;
    stackSize = 1;
  }

  /**
   * Skips the next value recursively. If it is an object or array, all nested
   * elements are skipped. This method is intended for use when the JSON token
   * stream contains unrecognized or unhandled values.
   */
  public void skipValue() {
    int count = 0;
    do {
      int p = peeked;
      if (p == PEEKED_NONE) {
        p = doPeek();
      }

      if (p == PEEKED_BEGIN_ARRAY) {
        push(JsonScope.EMPTY_ARRAY);
        count++;
      }
      else if (p == PEEKED_BEGIN_OBJECT) {
        push(JsonScope.EMPTY_OBJECT);
        count++;
      }
      else if (p == PEEKED_END_ARRAY || p == PEEKED_END_OBJECT) {
        stackSize--;
        count--;
      }
      else if (p == PEEKED_UNQUOTED_NAME || p == PEEKED_UNQUOTED) {
        skipUnquotedValue();
      }
      else if (p == PEEKED_SINGLE_QUOTED || p == PEEKED_SINGLE_QUOTED_NAME) {
        skipQuotedValue('\'');
      }
      else if (p == PEEKED_DOUBLE_QUOTED || p == PEEKED_DOUBLE_QUOTED_NAME) {
        skipQuotedValue('"');
      }
      else if (p == PEEKED_NUMBER) {
        position += peekedNumberLength;
      }
      peeked = PEEKED_NONE;
    }
    while (count != 0);
  }

  public void skipValues() {
    while (hasNext()) {
      skipValue();
    }
  }

  private void push(int newTop) {
    if (stackSize == stack.length) {
      int[] newStack = new int[stackSize * 2];
      System.arraycopy(stack, 0, newStack, 0, stackSize);
      stack = newStack;
    }
    stack[stackSize++] = newTop;
  }

  private int getLineNumber() {
    int result = 1;
    for (int i = 0; i < position; i++) {
      if (sourceSequence.charAt(i) == '\n') {
        result++;
      }
    }
    return result;
  }

  private int getColumnNumber() {
    int result = 1;
    for (int i = 0; i < position; i++) {
      if (sourceSequence.charAt(i) == '\n') {
        result = 1;
      }
      else {
        result++;
      }
    }
    return result;
  }

  /**
   * Returns the next character in the stream that is neither whitespace nor a
   * part of a comment. When this returns, the returned character is always at
   * {@code buffer[pos-1]}; this means the caller can always push back the
   * returned character by decrementing {@code position}.
   */
  private int nextNonWhitespace(boolean throwOnEof) {
    /*
     * This code uses ugly local variables 'p' and 'l' representing the 'pos'
     * and 'limit' fields respectively. Using locals rather than fields saves
     * a few field reads for each whitespace character in a pretty-printed
     * document, resulting in a 5% speedup. We need to flush 'p' to its field
     * before any (potentially indirect) call to fillBuffer() and reread both
     * 'p' and 'l' after any (potentially indirect) call to the same method.
     */
    CharSequence in = this.sourceSequence;
    int p = position;
    int l = limit;
    while (true) {
      if (p == l) {
        position = p;
        break;
      }

      int c = in.charAt(p++);
      if (c == '\n') {
        //lineNumber++;
        //lineStart = p;
        continue;
      }
      else if (c == ' ' || c == '\r' || c == '\t') {
        continue;
      }

      if (c == '/') {
        position = p;
        if (p == l) {
          position--; // push back '/' so it's still in the buffer when this method returns
          boolean charsLoaded = (position + 3) < limit;
          position++; // consume the '/' again
          if (!charsLoaded) {
            return c;
          }
        }

        checkLenient();
        char peek = in.charAt(position);
        switch (peek) {
          case '*' -> {
            // skip a /* c-style comment */
            position++;
            if (!skipTo("*/")) {
              throw createParseError("Unterminated comment");
            }
            p = position + 2;
            l = limit;
          }
          case '/' -> {
            // skip a // end-of-line comment
            position++;
            skipToEndOfLine();
            p = position;
            l = limit;
          }
          default -> {
            return c;
          }
        }
      }
      else if (c == '#') {
        position = p;
        /*
         * Skip a # hash end-of-line comment. The JSON RFC doesn't
         * specify this behaviour, but it's required to parse
         * existing documents. See http://b/2571423.
         */
        checkLenient();
        skipToEndOfLine();
        p = position;
        l = limit;
      }
      else {
        position = p;
        return c;
      }
    }
    if (throwOnEof) {
      throw createParseError("End of input");
    }
    else {
      return -1;
    }
  }

  private void checkLenient() {
    if (!lenient) {
      throw createParseError("Use JsonReaderEx.setLenient(true) to accept malformed JSON");
    }
  }

  /**
   * Advances the position until after the next newline character. If the line
   * is terminated by "\r\n", the '\n' must be consumed as whitespace by the
   * caller.
   */
  private void skipToEndOfLine() {
    while (position < limit) {
      char c = sourceSequence.charAt(position++);
      if (c == '\n' || c == '\r') {
        break;
      }
    }
  }

  private boolean skipTo(String toFind) {
    outer:
    for (; position + toFind.length() <= limit; position++) {
      if (sourceSequence.charAt(position) == '\n') {
        //lineNumber++;
        //lineStart = pos + 1;
        continue;
      }
      for (int c = 0; c < toFind.length(); c++) {
        if (sourceSequence.charAt(position + c) != toFind.charAt(c)) {
          continue outer;
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + " at line " + getLineNumber() + " column " + getColumnNumber();
  }

  /**
   * Unescapes the character identified by the character or characters that
   * immediately follow a backslash. The backslash '\' should have already
   * been read. This supports both unicode escapes "u000A" and two-character
   * escapes "\n".
   *
   * @throws NumberFormatException if any unicode escape sequences are
   *     malformed.
   */
  private char readEscapeCharacter() {
    if (position == limit) {
      throw createParseError("Unterminated escape sequence");
    }

    char escaped = sourceSequence.charAt(position++);
    return switch (escaped) {
      case 'u' -> {
        if (position + 4 > limit) {
          throw createParseError("Unterminated escape sequence");
        }
        // Equivalent to Integer.parseInt(stringPool.get(buffer, pos, 4), 16);
        char result = 0;
        for (int i = position, end = i + 4; i < end; i++) {
          char c = sourceSequence.charAt(i);
          result <<= 4;
          if (c >= '0' && c <= '9') {
            result += (c - '0');
          }
          else if (c >= 'a' && c <= 'f') {
            result += (c - 'a' + 10);
          }
          else if (c >= 'A' && c <= 'F') {
            result += (c - 'A' + 10);
          }
          else {
            throw new NumberFormatException("\\u" + sourceSequence.subSequence(position, position + 4));
          }
        }
        position += 4;
        yield result;
      }
      case 't' -> '\t';
      case 'b' -> '\b';
      case 'n' -> '\n';
      case 'r' -> '\r';
      case 'f' -> '\f';
      //case '\n', '\'', '"', '\\',
      default -> escaped;
    };
  }

  /**
   * Throws a new IO exception with the given message and a context snippet
   * with this reader's content.
   */
  private JsonParseException createParseError(String message) {
    throw new JsonParseException(message + " at line " + getLineNumber() + " column " + getColumnNumber());
  }
}