package com.intellij.execution.impl;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static com.intellij.execution.impl.ConsoleViewImpl.TokenInfo;
import static org.junit.Assert.assertEquals;

/**
 * @author Denis Zhdanov
 * @since 04/06/2011
 */
public class ConsoleBufferTest {

  private static final ConsoleViewContentType IMPORTANT_OUTPUT = new ConsoleViewContentType("IMPORTANT_OUTPUT", (TextAttributes)null);
  private static final ConsoleViewContentType NORMAL_OUTPUT = new ConsoleViewContentType("NORMAL_OUTPUT", (TextAttributes)null);
  private static final ConsoleViewContentType BORING_OUTPUT = new ConsoleViewContentType("BORING_OUTPUT", (TextAttributes)null);
  
  private ConsoleBuffer myBuffer;
  private int myBufferSize;
  private int myBufferUnitSize;
    
  @Before
  public void setUp() throws Exception {
    myBufferSize = 10;
    myBufferUnitSize = 3;
    init();
  }
  
  @Test
  public void nonExceedingBufferSize() {
    myBuffer.print("a", NORMAL_OUTPUT, null);
    checkState(s("a"), new TokenInfo(NORMAL_OUTPUT, 0, 1));
    
    myBuffer.print("b", BORING_OUTPUT, null);
    checkState(s("ab"), new TokenInfo(NORMAL_OUTPUT, 0, 1), new TokenInfo(BORING_OUTPUT, 1, 2));

    myBuffer.print("cd", BORING_OUTPUT, null);
    checkState(s("abc", "d"), new TokenInfo(NORMAL_OUTPUT, 0, 1), new TokenInfo(BORING_OUTPUT, 1, 4));

    myBuffer.print("ef", BORING_OUTPUT, null);
    checkState(s("abc", "def"), new TokenInfo(NORMAL_OUTPUT, 0, 1), new TokenInfo(BORING_OUTPUT, 1, 6));
    
    myBuffer.print("ghij", NORMAL_OUTPUT, null);
    checkState(
      s("abc", "def", "ghi", "j"),
      new TokenInfo(NORMAL_OUTPUT, 0, 1), new TokenInfo(BORING_OUTPUT, 1, 6), new TokenInfo(NORMAL_OUTPUT, 6, 10)
    );
  }
  
  @Test
  public void cycling() {
    myBuffer.print("abcdefghi", NORMAL_OUTPUT, null);
    myBuffer.print("jklm", BORING_OUTPUT, null);
    checkState(s("def", "ghi", "jkl", "m"), new TokenInfo(NORMAL_OUTPUT, 0, 6), new TokenInfo(BORING_OUTPUT, 6, 10));
  }
  
  @Test
  public void removedTokenOnCyclingIsNotShownAtDeferredTypes() {
    myBuffer.print("a", BORING_OUTPUT, null);
    myBuffer.print("bcdefghijklmn", NORMAL_OUTPUT, null);
    checkState(s("efg", "hij", "klm", "n"), new TokenInfo(NORMAL_OUTPUT, 0, 10));
  }
  
  @Test
  public void tokenDataIsTrimmed() {
    myBuffer.print("1234567890", NORMAL_OUTPUT, null);
    myBuffer.print("123456789012", NORMAL_OUTPUT, null);
    checkState(s("345", "678", "901", "2"), new TokenInfo(NORMAL_OUTPUT, 0, 10));
  }
  
  @Test
  public void removingOfGreatNumberOfTokens() {
    myBuffer.print("a", NORMAL_OUTPUT, null);
    myBuffer.print("b", BORING_OUTPUT, null);
    myBuffer.print("a", NORMAL_OUTPUT, null);
    myBuffer.print("b", BORING_OUTPUT, null);
    myBuffer.print("a", NORMAL_OUTPUT, null);
    myBuffer.print("1234567890", BORING_OUTPUT, null);

    checkState(s("123", "456", "789", "0"), new TokenInfo(BORING_OUTPUT, 0, 10));
  }
  
  @Test
  public void removeFromBufferEnd() {
    myBuffer.print("1234567", IMPORTANT_OUTPUT, null);
    myBuffer.print("890", NORMAL_OUTPUT, null);
    myBuffer.print("abc", BORING_OUTPUT, null);

    checkState(
      s("123", "456", "7ab", "c"),
      new TokenInfo(IMPORTANT_OUTPUT, 0, 7), new TokenInfo(BORING_OUTPUT, 7, 10)
    );
  }
  
  @Test
  public void smallRemoveAtStart() {
    myBuffer.print("abc", NORMAL_OUTPUT, null);
    myBuffer.print("def", BORING_OUTPUT, null);
    myBuffer.print("ghi", NORMAL_OUTPUT, null);
    myBuffer.print("k", BORING_OUTPUT, null);
    myBuffer.print("l", NORMAL_OUTPUT, null);

    checkState(
      s("bc", "def", "ghi", "kl"),
      new TokenInfo(NORMAL_OUTPUT, 0, 2), new TokenInfo(BORING_OUTPUT, 2, 5), new TokenInfo(NORMAL_OUTPUT, 5, 8),
      new TokenInfo(BORING_OUTPUT, 8, 9), new TokenInfo(NORMAL_OUTPUT, 9, 10)
    );
  }
  
  private static List<String> s(String ... strings) {
    return Arrays.asList(strings);
  }
  
  private void checkState(@NotNull List<String> expectedBuffers, @NotNull TokenInfo ... expectedTokens) {
    Deque<StringBuilder> actualBuffers = myBuffer.getDeferredOutput();
    assertEquals(expectedBuffers.size(), actualBuffers.size());
    int i = 0;
    for (StringBuilder actual : actualBuffers) {
      String expected = expectedBuffers.get(i++);
      assertEquals(expected, actual.toString());
    }

    List<TokenInfo> actualTokens = myBuffer.getDeferredTokens();
    assertEquals(expectedTokens.length, actualTokens.size());
    i = 0;
    Set<ConsoleViewContentType> contentTypes = new HashSet<>();
    for (TokenInfo actual : actualTokens) {
      TokenInfo expected = expectedTokens[i++];
      assertEquals(expected.contentType, actual.contentType);
      assertEquals(expected.startOffset, actual.startOffset);
      assertEquals(expected.endOffset, actual.endOffset);
      contentTypes.add(expected.contentType);
    }
    
    assertEquals(contentTypes, myBuffer.getDeferredTokenTypes());
  }
  
  private void init() throws Exception {
    myBuffer = new ConsoleBuffer(true, myBufferSize, myBufferUnitSize);
    myBuffer.setContentTypesToNotStripOnCycling(Collections.singleton(IMPORTANT_OUTPUT));
  }
}
