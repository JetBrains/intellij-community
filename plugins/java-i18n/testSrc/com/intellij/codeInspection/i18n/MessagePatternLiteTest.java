// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.i18n;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class MessagePatternLiteTest {
  @Test
  public void plainText() {
    assertEquals("Hello World", literalText(parse("Hello World")));
  }

  @Test
  public void doubledApostrophe() {
    assertEquals("Don't", literalText(parse("Don''t")));
  }

  @Test
  public void quotedTextWithBraces() {
    assertEquals("{0} is literal", literalText(parse("'{0}' is literal")));
  }

  @Test
  public void doubledApostropheInsideQuotedText() {
    assertEquals("It's", literalText(parse("'It''s'")));
  }

  @Test
  public void trailingApostrophe() {
    assertEquals("rock'", literalText(parse("rock'")));
  }

  @Test
  public void unterminatedQuoteAutoCloses() {
    assertEquals("dont know", literalText(parse("don't know")));
  }

  @Test
  public void numberedArg() {
    MessagePatternLite pattern = parse("Open {0} File");
    assertEquals(List.of("Open _ File"), samples(pattern));
    MessagePatternLite.Part argStart = findFirst(pattern, MessagePatternLite.Part.Type.ARG_START);
    assertEquals(MessagePatternLite.ArgType.NONE, argStart.getArgType());
    assertEquals(0, findFirst(pattern, MessagePatternLite.Part.Type.ARG_NUMBER).getValue());
  }

  @Test
  public void namedArg() {
    MessagePatternLite pattern = parse("{name} Selected");
    assertEquals(List.of("_ Selected"), samples(pattern));
    assertEquals("name", partText(pattern, findFirst(pattern, MessagePatternLite.Part.Type.ARG_NAME)));
  }

  @Test
  public void simpleArgWithStyle() {
    MessagePatternLite pattern = parse("{0,number,#,##0} Files");
    assertEquals(List.of("_ Files"), samples(pattern));
    assertEquals(MessagePatternLite.ArgType.SIMPLE, findFirst(pattern, MessagePatternLite.Part.Type.ARG_START).getArgType());
    assertEquals("number", partText(pattern, findFirst(pattern, MessagePatternLite.Part.Type.ARG_TYPE)));
    assertEquals("#,##0", partText(pattern, findFirst(pattern, MessagePatternLite.Part.Type.ARG_STYLE)));
  }

  @Test
  public void choice() {
    MessagePatternLite pattern = parse("Delete {0,choice,1#File|2#Files}?");
    assertEquals(MessagePatternLite.ArgType.CHOICE, findFirst(pattern, MessagePatternLite.Part.Type.ARG_START).getArgType());
    assertEquals(List.of("Delete File?", "Delete Files?"), samples(pattern));
  }

  @Test
  public void choiceSeparators() {
    assertEquals(List.of("none", "some", "many"), samples(parse("{0,choice,0#none|0<some|1≤many}")));
  }

  @Test
  public void pluralWithOffsetAndReplaceNumber() {
    MessagePatternLite pattern = parse("{0, plural, offset:1 one {# File in {1}} other {# Files}}");
    // the nested arg resets the sample builder's branch counters, so the first sample also picks up the "other" branch;
    // ICU produces the identical part sequence, and the inspection consumes it the same way
    assertEquals(List.of(" File in _ Files", " Files"), samples(pattern));
    assertEquals(2, Collections.frequency(partTypes(pattern), MessagePatternLite.Part.Type.REPLACE_NUMBER));
    assertEquals(1, findFirst(pattern, MessagePatternLite.Part.Type.ARG_INT).getValue());
  }

  @Test
  public void pluralWithExplicitValueSelector() {
    assertEquals(List.of("One", "Many"), samples(parse("{0, plural, =1 {One} other {Many}}")));
  }

  @Test
  public void quotedNumberSignInPluralBranch() {
    MessagePatternLite pattern = parse("{0,plural,other{'#' Files}}");
    assertFalse(partTypes(pattern).contains(MessagePatternLite.Part.Type.REPLACE_NUMBER));
    assertEquals(List.of("# Files"), samples(pattern));
  }

  @Test
  public void pluralAfterLiteralPrefix() {
    MessagePatternLite pattern = parse("Generate Code with {0, plural, one {Foo} other {Bar}}");
    assertEquals(List.of("Generate Code with Foo", "Generate Code with Bar"), samples(pattern));
    assertEquals(List.of(MessagePatternLite.Part.Type.MSG_START,
                         MessagePatternLite.Part.Type.ARG_START,
                         MessagePatternLite.Part.Type.ARG_NUMBER,
                         MessagePatternLite.Part.Type.ARG_SELECTOR,
                         MessagePatternLite.Part.Type.MSG_START,
                         MessagePatternLite.Part.Type.MSG_LIMIT,
                         MessagePatternLite.Part.Type.ARG_SELECTOR,
                         MessagePatternLite.Part.Type.MSG_START,
                         MessagePatternLite.Part.Type.MSG_LIMIT,
                         MessagePatternLite.Part.Type.ARG_LIMIT,
                         MessagePatternLite.Part.Type.MSG_LIMIT),
                 partTypes(pattern));
    assertEquals(10, pattern.getLimitPartIndex(0));
    assertEquals(9, pattern.getLimitPartIndex(1));
    assertEquals(5, pattern.getLimitPartIndex(4));
  }

  @Test
  public void select() {
    MessagePatternLite pattern = parse("{gender, select, male {He} female {She} other {They}}");
    assertEquals(MessagePatternLite.ArgType.SELECT, findFirst(pattern, MessagePatternLite.Part.Type.ARG_START).getArgType());
    assertEquals(List.of("He", "She", "They"), samples(pattern));
  }

  @Test
  public void selectOrdinal() {
    MessagePatternLite pattern = parse("{0, selectordinal, one {#st} other {#th}}");
    assertEquals(MessagePatternLite.ArgType.SELECTORDINAL, findFirst(pattern, MessagePatternLite.Part.Type.ARG_START).getArgType());
    assertEquals(List.of("st", "th"), samples(pattern));
  }

  @Test
  public void nestingLevels() {
    MessagePatternLite pattern = parse("{0,plural,other{{1,plural,other{deep}}}}");
    List<Integer> msgStartValues = new ArrayList<>();
    for (int i = 0; i < pattern.countParts(); i++) {
      MessagePatternLite.Part part = pattern.getPart(i);
      if (part.getType() == MessagePatternLite.Part.Type.MSG_START) {
        msgStartValues.add(part.getValue());
      }
    }
    assertEquals(List.of(0, 1, 2), msgStartValues);
  }

  @Test
  public void topLevelClosingBraceIsLiteralText() {
    assertEquals("a} b", literalText(parse("a} b")));
  }

  /// @noinspection NonAsciiCharacters
  @Test
  public void nonAsciiLettersAreIdentifierCharacters() {
    MessagePatternLite pattern = parse("{имя} Selected");
    assertEquals("имя", partText(pattern, findFirst(pattern, MessagePatternLite.Part.Type.ARG_NAME)));
  }

  @Test
  public void malformedPatternsThrow() {
    assertMalformed("Open {0");
    assertMalformed("Open {");
    assertMalformed("{}");
    assertMalformed("{foo bar}");
    assertMalformed("{01}");
    assertMalformed("{0,}");
    assertMalformed("{0,number");
    assertMalformed("{0,number,'#}");
    assertMalformed("{0,choice}");
    assertMalformed("{0,choice,}");
    assertMalformed("{0,choice,x#foo}");
    assertMalformed("{0,choice,1#foo");
    assertMalformed("{0,plural,}");
    assertMalformed("{0,plural,one{x}}");
    assertMalformed("{0,plural,=x {y} other {z}}");
    assertMalformed("{0,select,other}");
    // non-ASCII Pattern_Syntax characters end an identifier, like in ICU
    assertMalformed("{foo©}");
    assertMalformed("{a‰}");
    assertMalformed("{0,plural,one‰{x}other{y}}");
  }

  private static void assertMalformed(String pattern) {
    assertThrows(pattern, IllegalArgumentException.class, () -> parse(pattern));
  }

  private static MessagePatternLite parse(String pattern) {
    MessagePatternLite result = new MessagePatternLite();
    result.parse(pattern);
    return result;
  }

  /**
   * Joins the gaps between consecutive parts; the whole pattern must be a single message
   * (no arguments), so the result is exactly the literal output text.
   */
  private static String literalText(MessagePatternLite pattern) {
    assertFalse(partTypes(pattern).contains(MessagePatternLite.Part.Type.ARG_START));
    StringBuilder result = new StringBuilder();
    String string = pattern.getPatternString();
    for (int i = 1; i < pattern.countParts(); i++) {
      result.append(string, pattern.getPart(i - 1).getLimit(), pattern.getPatternIndex(i));
    }
    return result.toString();
  }

  /**
   * Mirrors the sample building in {@code TitleCapitalizationInspection.PropertyValue#isSatisfied}:
   * literal text is taken from the gaps between parts, plain arguments become "_",
   * and each choice/plural/select variant produces its own sample.
   */
  private static List<String> samples(MessagePatternLite pattern) {
    int parts = pattern.countParts();
    int maxMsgCount = 1;
    for (int i = 0; i < parts; i++) {
      maxMsgCount = Math.max(maxMsgCount, messagesForPart(pattern, i));
    }
    String string = pattern.getPatternString();
    List<String> result = new ArrayList<>();
    for (int curIndex = 0; curIndex < maxMsgCount; curIndex++) {
      StringBuilder sample = new StringBuilder();
      int msgIndex = 0;
      int nestingLevel = 0;
      int curMsgCount = 0;
      boolean inMsg = false;
      for (int i = 1; i < parts; i++) {
        MessagePatternLite.Part part = pattern.getPart(i);
        boolean shouldCopyPart = nestingLevel == 0 || inMsg && msgIndex == curIndex % curMsgCount + 1;
        if (shouldCopyPart) {
          sample.append(string, pattern.getPart(i - 1).getLimit(), pattern.getPatternIndex(i));
        }
        if (part.getType() == MessagePatternLite.Part.Type.ARG_START) {
          nestingLevel++;
          MessagePatternLite.ArgType argType = part.getArgType();
          if ((argType == MessagePatternLite.ArgType.SIMPLE || argType == MessagePatternLite.ArgType.NONE) && shouldCopyPart) {
            sample.append("_");
          }
          msgIndex = 0;
          curMsgCount = Math.max(1, messagesForPart(pattern, i));
        }
        else if (part.getType() == MessagePatternLite.Part.Type.MSG_START) {
          msgIndex++;
          inMsg = true;
        }
        else if (part.getType() == MessagePatternLite.Part.Type.MSG_LIMIT) {
          inMsg = false;
        }
        else if (part.getType() == MessagePatternLite.Part.Type.ARG_LIMIT) {
          nestingLevel--;
        }
      }
      result.add(sample.toString());
    }
    return result;
  }

  private static int messagesForPart(MessagePatternLite pattern, int index) {
    MessagePatternLite.Part part = pattern.getPart(index);
    if (part.getType() != MessagePatternLite.Part.Type.ARG_START) return 0;
    int limitPart = pattern.getLimitPartIndex(index);
    int msgCount = 0;
    int nesting = -1;
    for (int i = index + 1; i < limitPart; i++) {
      part = pattern.getPart(i);
      if (part.getType() == MessagePatternLite.Part.Type.MSG_START) {
        if (nesting == -1) {
          nesting = part.getValue();
        }
        else if (nesting != part.getValue()) {
          continue;
        }
        msgCount++;
      }
    }
    return msgCount;
  }

  private static List<MessagePatternLite.Part.Type> partTypes(MessagePatternLite pattern) {
    List<MessagePatternLite.Part.Type> types = new ArrayList<>();
    for (int i = 0; i < pattern.countParts(); i++) {
      types.add(pattern.getPart(i).getType());
    }
    return types;
  }

  private static MessagePatternLite.Part findFirst(MessagePatternLite pattern, MessagePatternLite.Part.Type type) {
    for (int i = 0; i < pattern.countParts(); i++) {
      MessagePatternLite.Part part = pattern.getPart(i);
      if (part.getType() == type) return part;
    }
    throw new AssertionError("No " + type + " part in " + pattern.getPatternString());
  }

  private static String partText(MessagePatternLite pattern, MessagePatternLite.Part part) {
    return pattern.getPatternString().substring(part.getIndex(), part.getLimit());
  }
}
