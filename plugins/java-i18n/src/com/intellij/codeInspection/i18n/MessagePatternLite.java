// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.i18n;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal replacement for ICU4J's {@code com.ibm.icu.text.MessagePattern} in {@code ApostropheMode.DOUBLE_REQUIRED} mode.
 * Parses a MessageFormat pattern into a flat list of parts covering every syntax character,
 * so literal text can be read from the gaps between consecutive parts
 * ({@code getPart(i - 1).getLimit()} .. {@code getPatternIndex(i)}).
 * Throws {@link IllegalArgumentException} on malformed patterns.
 * <p>
 * The parsing logic is derived from ICU4J's {@code MessagePattern}
 * (Copyright © 2010-2025 Unicode, Inc. and others; Unicode License v3, https://www.unicode.org/license.txt).
 */
public final class MessagePatternLite {
  public enum ArgType {
    NONE, SIMPLE, CHOICE, PLURAL, SELECT, SELECTORDINAL;

    boolean hasPluralStyle() {
      return this == PLURAL || this == SELECTORDINAL;
    }
  }

  private static final ArgType[] ARG_TYPES = ArgType.values();

  public static final class Part {
    public enum Type {
      MSG_START, MSG_LIMIT, SKIP_SYNTAX, REPLACE_NUMBER,
      ARG_START, ARG_LIMIT, ARG_NUMBER, ARG_NAME, ARG_TYPE, ARG_STYLE, ARG_SELECTOR, ARG_INT, ARG_DOUBLE
    }

    private final Type myType;
    private final int myIndex;
    private final int myLength;
    private int myValue;
    private int myLimitPartIndex;

    private Part(Type type, int index, int length, int value) {
      myType = type;
      myIndex = index;
      myLength = length;
      myValue = value;
    }

    public Type getType() {
      return myType;
    }

    public int getIndex() {
      return myIndex;
    }

    public int getLimit() {
      return myIndex + myLength;
    }

    public int getValue() {
      return myValue;
    }

    public ArgType getArgType() {
      return myType == Type.ARG_START || myType == Type.ARG_LIMIT ? ARG_TYPES[myValue] : ArgType.NONE;
    }

    @Override
    public String toString() {
      String value = myType == Type.ARG_START || myType == Type.ARG_LIMIT ? getArgType().name() : Integer.toString(myValue);
      return myType.name() + "(" + value + ")@" + myIndex;
    }
  }

  private static final int ARG_NAME_NOT_NUMBER = -1;
  private static final int ARG_NAME_NOT_VALID = -2;

  private String myMessage;
  private final List<Part> myParts = new ArrayList<>();

  public void parse(@NotNull String pattern) {
    myMessage = pattern;
    myParts.clear();
    parseMessage(0, 0, 0, ArgType.NONE);
  }

  public String getPatternString() {
    return myMessage;
  }

  public int countParts() {
    return myParts.size();
  }

  public Part getPart(int i) {
    return myParts.get(i);
  }

  public int getPatternIndex(int partIndex) {
    return myParts.get(partIndex).myIndex;
  }

  public int getLimitPartIndex(int start) {
    return Math.max(myParts.get(start).myLimitPartIndex, start);
  }

  private int parseMessage(int index, int msgStartLength, int nestingLevel, ArgType parentType) {
    int msgStart = myParts.size();
    addPart(Part.Type.MSG_START, index, msgStartLength, nestingLevel);
    index += msgStartLength;
    while (index < myMessage.length()) {
      char c = myMessage.charAt(index++);
      if (c == '\'') {
        index = parseQuotedText(index);
      }
      else if (parentType.hasPluralStyle() && c == '#') {
        addPart(Part.Type.REPLACE_NUMBER, index - 1, 1, 0);
      }
      else if (c == '{') {
        index = parseArg(index - 1, nestingLevel);
      }
      else if (nestingLevel > 0 && c == '}' || parentType == ArgType.CHOICE && c == '|') {
        int limitLength = parentType == ArgType.CHOICE && c == '}' ? 0 : 1;
        addLimitPart(msgStart, Part.Type.MSG_LIMIT, index - 1, limitLength, nestingLevel);
        return parentType == ArgType.CHOICE ? index - 1 : index;
      }
    }
    if (nestingLevel > 0) {
      throw unmatchedBraces();
    }
    addLimitPart(msgStart, Part.Type.MSG_LIMIT, index, 0, nestingLevel);
    return index;
  }

  private int parseQuotedText(int index) {
    if (index == myMessage.length()) {
      return index; // trailing apostrophe stays literal text
    }
    if (myMessage.charAt(index) == '\'') {
      addPart(Part.Type.SKIP_SYNTAX, index, 1, 0); // double apostrophe encodes a literal one
      return index + 1;
    }
    addPart(Part.Type.SKIP_SYNTAX, index - 1, 1, 0); // quote-starting apostrophe
    while (true) {
      index = myMessage.indexOf('\'', index + 1);
      if (index < 0) {
        return myMessage.length(); // unterminated quoted text reaches the end of the message
      }
      if (index + 1 < myMessage.length() && myMessage.charAt(index + 1) == '\'') {
        addPart(Part.Type.SKIP_SYNTAX, ++index, 1, 0); // second apostrophe of a doubled pair inside quoted text
      }
      else {
        addPart(Part.Type.SKIP_SYNTAX, index, 1, 0); // quote-ending apostrophe
        return index + 1;
      }
    }
  }

  private int parseArg(int index, int nestingLevel) {
    int argStart = myParts.size();
    ArgType argType = ArgType.NONE;
    addPart(Part.Type.ARG_START, index, 1, 0);
    int nameIndex = index = skipWhiteSpace(index + 1);
    if (index == myMessage.length()) {
      throw unmatchedBraces();
    }
    index = skipIdentifier(index);
    int number = parseArgNumber(nameIndex, index);
    if (number >= 0) {
      addPart(Part.Type.ARG_NUMBER, nameIndex, index - nameIndex, number);
    }
    else if (number == ARG_NAME_NOT_NUMBER) {
      addPart(Part.Type.ARG_NAME, nameIndex, index - nameIndex, 0);
    }
    else {
      throw badArgumentSyntax(nameIndex);
    }
    index = skipWhiteSpace(index);
    if (index == myMessage.length()) {
      throw unmatchedBraces();
    }
    char c = myMessage.charAt(index);
    if (c == '}') {
      // untyped argument like {0}
    }
    else if (c != ',') {
      throw badArgumentSyntax(nameIndex);
    }
    else {
      int typeIndex = index = skipWhiteSpace(index + 1);
      while (index < myMessage.length() && isArgTypeChar(myMessage.charAt(index))) {
        index++;
      }
      int length = index - typeIndex;
      index = skipWhiteSpace(index);
      if (index == myMessage.length()) {
        throw unmatchedBraces();
      }
      if (length == 0 || (c = myMessage.charAt(index)) != ',' && c != '}') {
        throw badArgumentSyntax(nameIndex);
      }
      argType = argTypeForName(typeIndex, length);
      myParts.get(argStart).myValue = argType.ordinal();
      if (argType == ArgType.SIMPLE) {
        addPart(Part.Type.ARG_TYPE, typeIndex, length, 0);
      }
      if (c == '}') {
        if (argType != ArgType.SIMPLE) {
          throw new IllegalArgumentException("No style field for complex argument: " + prefix(nameIndex));
        }
      }
      else {
        index++;
        if (argType == ArgType.SIMPLE) {
          index = parseSimpleStyle(index);
        }
        else if (argType == ArgType.CHOICE) {
          index = parseChoiceStyle(index, nestingLevel);
        }
        else {
          index = parsePluralOrSelectStyle(argType, index, nestingLevel);
        }
      }
    }
    addLimitPart(argStart, Part.Type.ARG_LIMIT, index, 1, argType.ordinal());
    return index + 1;
  }

  private ArgType argTypeForName(int typeIndex, int length) {
    if (length == 6) {
      if (myMessage.regionMatches(true, typeIndex, "choice", 0, 6)) return ArgType.CHOICE;
      if (myMessage.regionMatches(true, typeIndex, "plural", 0, 6)) return ArgType.PLURAL;
      if (myMessage.regionMatches(true, typeIndex, "select", 0, 6)) return ArgType.SELECT;
    }
    else if (length == 13 && myMessage.regionMatches(true, typeIndex, "selectordinal", 0, 13)) {
      return ArgType.SELECTORDINAL;
    }
    return ArgType.SIMPLE;
  }

  private int parseSimpleStyle(int index) {
    int start = index;
    int nestedBraces = 0;
    while (index < myMessage.length()) {
      char c = myMessage.charAt(index++);
      if (c == '\'') {
        index = myMessage.indexOf('\'', index);
        if (index < 0) {
          throw new IllegalArgumentException("Quoted literal argument style text reaches to the end of the message: " + prefix(start));
        }
        index++;
      }
      else if (c == '{') {
        nestedBraces++;
      }
      else if (c == '}') {
        if (nestedBraces > 0) {
          nestedBraces--;
        }
        else {
          index--;
          addPart(Part.Type.ARG_STYLE, start, index - start, 0);
          return index;
        }
      }
    }
    throw unmatchedBraces();
  }

  private int parseChoiceStyle(int index, int nestingLevel) {
    int start = index;
    index = skipWhiteSpace(index);
    if (index == myMessage.length() || myMessage.charAt(index) == '}') {
      throw new IllegalArgumentException("Missing choice argument pattern in " + prefix(0));
    }
    while (true) {
      int numberIndex = index;
      index = skipDouble(index);
      if (index == numberIndex) {
        throw new IllegalArgumentException("Bad choice pattern syntax: " + prefix(start));
      }
      addNumericPart(numberIndex, index, true);
      index = skipWhiteSpace(index);
      if (index == myMessage.length()) {
        throw new IllegalArgumentException("Bad choice pattern syntax: " + prefix(start));
      }
      char c = myMessage.charAt(index);
      if (c != '#' && c != '<' && c != '≤') {
        throw new IllegalArgumentException(
          "Expected choice separator (#<≤) instead of '" + c + "' in choice pattern " + prefix(start));
      }
      addPart(Part.Type.ARG_SELECTOR, index, 1, 0);
      index = parseMessage(index + 1, 0, nestingLevel + 1, ArgType.CHOICE);
      if (index == myMessage.length() || myMessage.charAt(index) == '}') {
        return index;
      }
      index = skipWhiteSpace(index + 1); // skip the '|'
    }
  }

  private int parsePluralOrSelectStyle(ArgType argType, int index, int nestingLevel) {
    int start = index;
    boolean isEmpty = true;
    boolean hasOther = false;
    while (true) {
      index = skipWhiteSpace(index);
      if (index == myMessage.length()) {
        throw badStyleSyntax(argType, start);
      }
      if (myMessage.charAt(index) == '}') {
        if (!hasOther) {
          throw new IllegalArgumentException("Missing 'other' keyword in " + styleName(argType) + " pattern in " + prefix(0));
        }
        return index;
      }
      int selectorIndex = index;
      if (argType.hasPluralStyle() && myMessage.charAt(selectorIndex) == '=') {
        index = skipDouble(index + 1);
        if (index - selectorIndex == 1) {
          throw badStyleSyntax(argType, start);
        }
        addPart(Part.Type.ARG_SELECTOR, selectorIndex, index - selectorIndex, 0);
        addNumericPart(selectorIndex + 1, index, false);
      }
      else {
        index = skipIdentifier(index);
        int length = index - selectorIndex;
        if (length == 0) {
          throw badStyleSyntax(argType, start);
        }
        if (argType.hasPluralStyle() && length == 6 && index < myMessage.length() &&
            myMessage.regionMatches(selectorIndex, "offset:", 0, 7)) {
          if (!isEmpty) {
            throw new IllegalArgumentException(
              "Plural argument 'offset:' (if present) must precede key-message pairs: " + prefix(start));
          }
          int valueIndex = skipWhiteSpace(index + 1); // the ':' is at index
          index = skipDouble(valueIndex);
          if (index == valueIndex) {
            throw new IllegalArgumentException("Missing value for plural 'offset:' " + prefix(start));
          }
          addNumericPart(valueIndex, index, false);
          isEmpty = false;
          continue; // no message fragment after the offset
        }
        addPart(Part.Type.ARG_SELECTOR, selectorIndex, length, 0);
        if (myMessage.regionMatches(selectorIndex, "other", 0, length)) {
          hasOther = true;
        }
      }
      index = skipWhiteSpace(index);
      if (index == myMessage.length() || myMessage.charAt(index) != '{') {
        throw new IllegalArgumentException(
          "No message fragment after " + styleName(argType) + " selector: " + prefix(selectorIndex));
      }
      index = parseMessage(index, 1, nestingLevel + 1, argType);
      isEmpty = false;
    }
  }

  private int parseArgNumber(int start, int limit) {
    if (start >= limit) {
      return ARG_NAME_NOT_VALID;
    }
    char c = myMessage.charAt(start++);
    int number;
    boolean badNumber;
    if (c == '0') {
      if (start == limit) {
        return 0;
      }
      number = 0;
      badNumber = true; // leading zero
    }
    else if ('1' <= c && c <= '9') {
      number = c - '0';
      badNumber = false;
    }
    else {
      return ARG_NAME_NOT_NUMBER;
    }
    while (start < limit) {
      c = myMessage.charAt(start++);
      if (c < '0' || c > '9') {
        return ARG_NAME_NOT_NUMBER;
      }
      if (number >= Integer.MAX_VALUE / 10) {
        badNumber = true; // overflow
      }
      else {
        number = number * 10 + c - '0';
      }
    }
    return badNumber ? ARG_NAME_NOT_VALID : number;
  }

  private void addNumericPart(int start, int limit, boolean allowInfinity) {
    int index = start;
    char c = myMessage.charAt(index++);
    int isNegative = 0;
    if (c == '-') {
      isNegative = 1;
    }
    if ((isNegative != 0 || c == '+') && index < limit) {
      c = myMessage.charAt(index++);
    }
    if (c == '∞' && allowInfinity && index == limit) {
      addPart(Part.Type.ARG_DOUBLE, start, limit - start, 0);
      return;
    }
    int value = 0;
    while ('0' <= c && c <= '9') {
      value = value * 10 + c - '0';
      if (value > Short.MAX_VALUE + isNegative) {
        break; // not a small-enough integer
      }
      if (index == limit) {
        addPart(Part.Type.ARG_INT, start, limit - start, isNegative != 0 ? -value : value);
        return;
      }
      c = myMessage.charAt(index++);
    }
    // validation only: NumberFormatException is an IllegalArgumentException
    //noinspection ResultOfMethodCallIgnored
    Double.parseDouble(myMessage.substring(start, limit));
    addPart(Part.Type.ARG_DOUBLE, start, limit - start, 0);
  }

  private int skipWhiteSpace(int index) {
    while (index < myMessage.length() && isPatternWhiteSpace(myMessage.charAt(index))) {
      index++;
    }
    return index;
  }

  private int skipIdentifier(int index) {
    while (index < myMessage.length()) {
      char c = myMessage.charAt(index);
      if (isPatternWhiteSpace(c) || isPatternSyntax(c)) {
        break;
      }
      index++;
    }
    return index;
  }

  private int skipDouble(int index) {
    while (index < myMessage.length()) {
      char c = myMessage.charAt(index);
      if (c < '0' && c != '+' && c != '-' && c != '.' || c > '9' && c != 'e' && c != 'E' && c != '∞') {
        break;
      }
      index++;
    }
    return index;
  }

  private static boolean isPatternWhiteSpace(char c) {
    return c == '\t' || c == '\n' || c == 0x000B || c == '\f' || c == '\r' ||
           c == ' ' || c == 0x0085 || c == 0x200E || c == 0x200F || c == 0x2028 || c == 0x2029;
  }

  // the Unicode Pattern_Syntax property; the property is immutable by Unicode stability policy,
  // and these ranges match UCharacter.hasBinaryProperty(c, PATTERN_SYNTAX) of ICU4J 78.3 over the whole BMP
  private static boolean isPatternSyntax(char c) {
    if (c < 0x7F) {
      return c > 0x20 && !('0' <= c && c <= '9') && !('a' <= c && c <= 'z') && !('A' <= c && c <= 'Z') && c != '_';
    }
    if (c < 0xA1) return false;
    if (c <= 0xFF) {
      return c <= 0xA7 || c == 0xA9 || c == 0xAB || c == 0xAC || c == 0xAE ||
             c == 0xB0 || c == 0xB1 || c == 0xB6 || c == 0xBB || c == 0xBF || c == 0xD7 || c == 0xF7;
    }
    if (c < 0x2010) return false;
    if (c <= 0x3030) {
      return c <= 0x2027 || 0x2030 <= c && c <= 0x203E || 0x2041 <= c && c <= 0x2053 || 0x2055 <= c && c <= 0x205E ||
             0x2190 <= c && c <= 0x245F || 0x2500 <= c && c <= 0x2775 || 0x2794 <= c && c <= 0x2BFF ||
             0x2E00 <= c && c <= 0x2E7F || 0x3001 <= c && c <= 0x3003 || 0x3008 <= c && c <= 0x3020 || c == 0x3030;
    }
    return c == 0xFD3E || c == 0xFD3F || c == 0xFE45 || c == 0xFE46;
  }

  private static boolean isArgTypeChar(char c) {
    return 'a' <= c && c <= 'z' || 'A' <= c && c <= 'Z';
  }

  private void addPart(Part.Type type, int index, int length, int value) {
    myParts.add(new Part(type, index, length, value));
  }

  private void addLimitPart(int start, Part.Type type, int index, int length, int value) {
    myParts.get(start).myLimitPartIndex = myParts.size();
    addPart(type, index, length, value);
  }

  private IllegalArgumentException unmatchedBraces() {
    return new IllegalArgumentException("Unmatched '{' braces in message " + prefix(0));
  }

  private IllegalArgumentException badArgumentSyntax(int nameIndex) {
    return new IllegalArgumentException("Bad argument syntax: " + prefix(nameIndex));
  }

  private IllegalArgumentException badStyleSyntax(ArgType argType, int start) {
    return new IllegalArgumentException("Bad " + styleName(argType) + " pattern syntax: " + prefix(start));
  }

  private static String styleName(ArgType argType) {
    return argType.name().toLowerCase(Locale.ENGLISH);
  }

  private String prefix(int start) {
    int end = Math.min(myMessage.length(), start + 24);
    return "\"" + myMessage.substring(start, end) + (end < myMessage.length() ? " ...\"" : "\"");
  }
}
