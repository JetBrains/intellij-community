package com.intellij.execution.impl;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class ConsoleBufferTest extends UsefulTestCase {
  private static final ConsoleViewContentType IMPORTANT_OUTPUT = new ConsoleViewContentType("IMPORTANT_OUTPUT", (TextAttributes)null);
  private static final ConsoleViewContentType NORMAL_OUTPUT = new ConsoleViewContentType("NORMAL_OUTPUT", (TextAttributes)null);
  private static final ConsoleViewContentType BORING_OUTPUT = new ConsoleViewContentType("BORING_OUTPUT", (TextAttributes)null);
  
  private ConsoleBuffer myBuffer;
  private int myBufferSize;
  private int myBufferUnitSize;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myBufferSize = isPerformanceTest() ? 1024 : 10;
    myBufferUnitSize = isPerformanceTest() ? 256 : 3;
    init();
  }
  
  public void testNonExceedingBufferSize() {
    myBuffer.print("a", NORMAL_OUTPUT, null);
    checkState(s("a"), new ConsoleViewImpl.TokenInfo(NORMAL_OUTPUT, 0, 1, null));
    
    myBuffer.print("b", BORING_OUTPUT, null);
    checkState(s("ab"), new ConsoleViewImpl.TokenInfo(NORMAL_OUTPUT, 0, 1, null), new ConsoleViewImpl.TokenInfo(BORING_OUTPUT, 1, 2, null));

    myBuffer.print("cd", BORING_OUTPUT, null);
    checkState(s("abc", "d"), new ConsoleViewImpl.TokenInfo(NORMAL_OUTPUT, 0, 1, null), new ConsoleViewImpl.TokenInfo(BORING_OUTPUT, 1, 4, null));

    myBuffer.print("ef", BORING_OUTPUT, null);
    checkState(s("abc", "def"), new ConsoleViewImpl.TokenInfo(NORMAL_OUTPUT, 0, 1, null), new ConsoleViewImpl.TokenInfo(BORING_OUTPUT, 1, 6, null));
    
    myBuffer.print("ghij", NORMAL_OUTPUT, null);
    checkState(
      s("abc", "def", "ghi", "j"),
      new ConsoleViewImpl.TokenInfo(NORMAL_OUTPUT, 0, 1, null), new ConsoleViewImpl.TokenInfo(BORING_OUTPUT, 1, 6, null), new ConsoleViewImpl.TokenInfo(NORMAL_OUTPUT, 6, 10, null)
    );
  }
  
  public void testCycling() {
    myBuffer.print("abcdefghi", NORMAL_OUTPUT, null);
    myBuffer.print("jklm", BORING_OUTPUT, null);
    checkState(s("def", "ghi", "jkl", "m"), new ConsoleViewImpl.TokenInfo(NORMAL_OUTPUT, 0, 6, null), new ConsoleViewImpl.TokenInfo(BORING_OUTPUT, 6, 10, null));
  }
  
  public void testRemovedTokenOnCyclingIsNotShownAtDeferredTypes() {
    myBuffer.print("a", BORING_OUTPUT, null);
    myBuffer.print("bcdefghijklmn", NORMAL_OUTPUT, null);
    checkState(s("efg", "hij", "klm", "n"), new ConsoleViewImpl.TokenInfo(NORMAL_OUTPUT, 0, 10, null));
  }
  
  public void testTokenDataIsTrimmed() {
    myBuffer.print("1234567890", NORMAL_OUTPUT, null);
    myBuffer.print("123456789012", NORMAL_OUTPUT, null);
    checkState(s("345", "678", "901", "2"), new ConsoleViewImpl.TokenInfo(NORMAL_OUTPUT, 0, 10, null));
  }
  
  public void testRemovingOfGreatNumberOfTokens() {
    myBuffer.print("a", NORMAL_OUTPUT, null);
    myBuffer.print("b", BORING_OUTPUT, null);
    myBuffer.print("a", NORMAL_OUTPUT, null);
    myBuffer.print("b", BORING_OUTPUT, null);
    myBuffer.print("a", NORMAL_OUTPUT, null);
    myBuffer.print("1234567890", BORING_OUTPUT, null);

    checkState(s("123", "456", "789", "0"), new ConsoleViewImpl.TokenInfo(BORING_OUTPUT, 0, 10, null));
  }
  
  public void testRemoveFromBufferEnd() {
    myBuffer.print("1234567", IMPORTANT_OUTPUT, null);
    myBuffer.print("890", NORMAL_OUTPUT, null);
    myBuffer.print("abc", BORING_OUTPUT, null);

    checkState(
      s("123", "456", "7ab", "c"),
      new ConsoleViewImpl.TokenInfo(IMPORTANT_OUTPUT, 0, 7, null), new ConsoleViewImpl.TokenInfo(BORING_OUTPUT, 7, 10, null)
    );
  }
  
  public void testSmallRemoveAtStart() {
    myBuffer.print("abc", NORMAL_OUTPUT, null);
    myBuffer.print("def", BORING_OUTPUT, null);
    myBuffer.print("ghi", NORMAL_OUTPUT, null);
    myBuffer.print("k", BORING_OUTPUT, null);
    myBuffer.print("l", NORMAL_OUTPUT, null);

    checkState(
      s("bc", "def", "ghi", "kl"),
      new ConsoleViewImpl.TokenInfo(NORMAL_OUTPUT, 0, 2, null), new ConsoleViewImpl.TokenInfo(BORING_OUTPUT, 2, 5, null), new ConsoleViewImpl.TokenInfo(NORMAL_OUTPUT, 5, 8, null),
      new ConsoleViewImpl.TokenInfo(BORING_OUTPUT, 8, 9, null), new ConsoleViewImpl.TokenInfo(NORMAL_OUTPUT, 9, 10, null)
    );
  }
  
  private static List<String> s(String ... strings) {
    return Arrays.asList(strings);
  }
  
  private void checkState(@NotNull List<String> expectedBuffers, @NotNull ConsoleViewImpl.TokenInfo... expectedTokens) {
    Collection<StringBuilder> actualBuffers = myBuffer.myDeferredOutput;
    assertEquals(expectedBuffers.size(), actualBuffers.size());
    int i = 0;
    for (StringBuilder actual : actualBuffers) {
      String expected = expectedBuffers.get(i++);
      assertEquals(expected, actual.toString());
    }

    List<ConsoleViewImpl.TokenInfo> actualTokens = myBuffer.getTokens();
    assertEquals(expectedTokens.length, actualTokens.size());
    i = 0;
    Set<ConsoleViewContentType> contentTypes = new HashSet<>();
    for (ConsoleViewImpl.TokenInfo actual : actualTokens) {
      ConsoleViewImpl.TokenInfo expected = expectedTokens[i++];
      assertEquals(expected.contentType, actual.contentType);
      assertEquals(expected.startOffset, actual.startOffset);
      assertEquals(expected.endOffset, actual.endOffset);
      contentTypes.add(expected.contentType);
    }
    
    assertEquals(contentTypes, new HashSet<>(getAllDeferredTokenTypes(myBuffer.getTokens())));
  }

  // returns all ConsoleViewContentType mentioned in myTokens
  @NotNull
  Collection<ConsoleViewContentType> getAllDeferredTokenTypes(List<ConsoleViewImpl.TokenInfo> myTokens) {
    return myTokens.stream().map(info -> info.contentType).collect(Collectors.toSet());
  }

  private void init() throws Exception {
    myBuffer = new ConsoleBuffer(true, myBufferSize, myBufferUnitSize);
    myBuffer.setContentTypesToNotStripOnCycling(Collections.singleton(IMPORTANT_OUTPUT));
  }

  public void testPerformance() {
    PlatformTestUtil.startPerformanceTest("slow console.print", 9 * 60 * 1000, ()->{
      myBuffer.clear();
      for (int i=0; i<20000000; i++) {
        myBuffer.print("abc", NORMAL_OUTPUT, null);
        myBuffer.print("ii\n", BORING_OUTPUT, null);
        if (i > 1000000) {
          int fi = 0;
        }
      }
    }).cpuBound().assertTiming();
  }
}
