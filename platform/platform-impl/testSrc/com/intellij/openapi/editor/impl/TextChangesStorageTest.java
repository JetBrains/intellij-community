/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;

/**
 * @author Denis Zhdanov
 * @since 03/02/2011
 */
public class TextChangesStorageTest {

  private TextChangesStorage myStorage;  
    
  @Before
  public void setUp() {
    myStorage = new TextChangesStorage();
  }
  
  @Test
  public void clear() {
    assertTrue(myStorage.isEmpty());
    
    insert("abc", 2);
    assertFalse(myStorage.isEmpty());
    assertEquals(1, myStorage.getChanges().size());
    
    myStorage.clear();
    assertTrue(myStorage.isEmpty());
    assertTrue(myStorage.getChanges().isEmpty());
  }
  
  @Test
  public void singleInsert() {
    insert("abc", 2);
    checkChanges(c("abc", 2));
  }
  
  @Test
  public void singleLongInsert() {
    String text = "this is a relatively long text";
    insert(text, 2);
    checkChanges(c(text, 2));
  }

  @Test
  public void disconnectedInserts() {
    insert("abc", 2);
    insert("def", 6);
    insert("ghi", 11);
    checkChanges(c("abc", 2), c("def", 3), c("ghi", 5));
  }
  
  @Test
  public void disconnectedInsertsFromTailToStart() {
    insert("abc", 10);
    insert("def", 1);
    checkChanges(c("def", 1), c("abc", 10));
  }
  
  @Test
  public void adjacentInserts() {
    insert("abc", 2);
    insert("def", 5);
    insert("ghi", 8);
    checkChanges(c("abcdefghi", 2));
  }
  
  @Test
  public void nestedInserts() {
    insert("abc", 2);
    insert("XY", 3);
    insert("1234", 4);
    checkChanges(c("aX1234Ybc", 2));
  }
  
  @Test
  public void singleDelete() {
    delete(2, 3);
    checkChanges(c("", 2, 3));
  }
  
  @Test
  public void disconnectedDeletes() {
    delete(2, 3);
    delete(3, 4);
    delete(5, 6);
    checkChanges(c("", 2, 3), c("", 4, 5), c("", 7, 8));
  }
  
  @Test
  public void adjacentDeletes() {
    delete(2, 3);
    delete(2, 3);
    delete(2, 3);
    checkChanges(c("", 2, 5));
  }

  @Test
  public void adjacentDeletesFromEndToStart() {
    delete(5, 6);
    delete(4, 5);
    checkChanges(c("", 4, 6));
  }
  
  @Test
  public void singleReplace() {
    replace("abc", 3, 4);
    checkChanges(c("abc", 3, 4));
  }
  
  @Test
  public void disconnectedReplaces() {
    replace("abc", 3, 4);
    replace("de", 7, 8);
    replace("fghi", 10, 11);
    checkChanges(c("abc", 3, 4), c("de", 5, 6), c("fghi", 7, 8));
  }
  
  @Test
  public void disconnectedUnorderedReplaces() {
    replace("abc", 1, 4);
    replace("def", 10, 13);
    replace("ghi", 6, 9);
    checkChanges(c("abc", 1, 4), c("ghi", 6, 9), c("def", 10, 13));
  }
  
  @Test
  public void adjacentReplaces() {
    replace("abc", 3, 4);
    replace("de", 6, 9);
    replace("fghi", 8, 9);
    checkChanges(c("abcdefghi", 3, 8));
  }
  
  @Test
  public void intersectedReplaces() {
    replace("abc", 3, 4);
    replace("defg", 5, 6);
    replace("hi", 8, 11);
    checkChanges(c("abdefhi", 3, 6));
  }

  @Test
  public void intersectedReplacesFromEndToStart() {
    replace("abcd", 5, 6);
    replace("ef", 4, 7);
    replace("g", 1, 5);
    checkChanges(c("gfcd", 1, 6));
  }
  
  @Test
  public void nestedReplaces() {
    replace("abcdef", 3, 5);
    replace("gh", 4, 7);
    replace("i", 5, 6);
    checkChanges(c("agief", 3, 5));
  }
  
  @Test
  public void exactMultipleReplace() {
    replace("abc", 3, 4);
    replace("cde", 3, 6);
    replace("fg", 3, 6);
    checkChanges(c("fg", 3, 4));
  }
  
  @Test
  public void insertAndExactDelete() {
    insert("abc", 3);
    delete(3, 6);
    checkChanges();
  }
  
  @Test
  public void insertAndDeleteInTheMiddle() {
    insert("abc", 3);
    delete(4, 6);
    checkChanges(c("a", 3));
  }
  
  @Test
  public void insertAndWiderDelete() {
    insert("abc", 3);
    delete(2, 7);
    checkChanges(c("", 2, 4));
  }
  
  @Test
  public void insertAndDeleteFromLeft() {
    insert("abc", 3);
    delete(2, 5);
    checkChanges(c("c", 2, 3));
  }

  @Test
  public void insertAndDeleteFromRight() {
    insert("abc", 3);
    delete(4, 7);
    checkChanges(c("a", 3, 4));
  }
  
  @Test
  public void disconnectedInsertsAndExactLinkingDelete() {
    insert("a", 1);
    insert("bcd", 3);
    insert("efg", 8);
    delete(3, 11);
    checkChanges(c("a", 1), c("", 2, 4));
  }

  @Test
  public void disconnectedInsertsAndWiderLinkingDelete() {
    insert("abc", 3);
    insert("def", 8);
    delete(2, 13);
    checkChanges(c("", 2, 7));
  }

  @Test
  public void disconnectedInsertsAndNarrowLinkingDelete() {
    insert("abc", 3);
    insert("def", 8);
    delete(4, 9);
    checkChanges(c("aef", 3, 5));
  }
  
  @Test
  public void replaceAndDeleteWholeTextFromItsStart() {
    delete(72, 79);
    insert("a", 72);
    delete(72, 73);
    delete(64, 71);
    delete(51, 62);
    insert("a", 54);
    insert("a", 53);
    insert("a", 51);
    delete(56, 57);
    insert("b", 56);
    checkChanges(c("a", 51, 62), c("a", 64, 71), c("b", 72, 79));
  }
  
  @Test
  public void exactRemoveOfPreviousInsert() {
    insert("a", 1);
    insert("bcd", 3);
    insert("efg", 7);
    delete(3, 6);
    checkChanges(c("a", 1), c("efg", 3));
  }

  @Test
  public void removeAdjacentToInsert() {
    insert("a", 1);
    insert("bc", 3);
    delete(2, 3);
    checkChanges(c("abc", 1, 2));
  }
  
  private void checkChanges(TextChangeImpl ... changes) {
    assertEquals(asList(changes), myStorage.getChanges());
    assertEquals(changes.length > 0, !myStorage.isEmpty());
    if (changes.length <= 0) {
      return;
    }
    int length = changes[changes.length - 1].getEnd() + 2;
    char[] input = new char[length];
    char c = 'A';
    for (int i = 0; i < input.length; i++) {
      input[i] = c++;
    }
    char[] output = BulkChangesMerger.INSTANCE.mergeToCharArray(input, input.length, asList(changes));
    
    // charAt().
    for (int i = 0; i < output.length; i++) {
      if (output[i] != myStorage.charAt(input, i)) {
        fail(String.format(
          "Detected incorrect charAt() processing. Original text: '%s', changes: %s, index: %d, expected: %c, actual: %c",
          new String(input), Arrays.asList(changes), i, output[i], myStorage.charAt(input, i)
        ));
      }
    }
    
    // substring().
    for (int start = 0; start < output.length; start++) {
      for( int end = start; end < output.length; end++) {
        String expected = new String(output, start, end - start);
        String actual = myStorage.substring(input, start, end).toString();
        if (!expected.equals(actual)) {
          fail(String.format(
            "Detected incorrect substring() processing. Original text: '%s', changes: %s, client text: '%s', range: %d-%d, "
            + "expected: '%s', actual: '%s'", new String(input), Arrays.asList(changes), new String(output), start, end, expected, actual
          ));
        }
      }
    }
  }
  
  private static TextChangeImpl c(@NotNull String text, int startOffset) {
    return c(text, startOffset, startOffset);
  }
  
  private static TextChangeImpl c(@NotNull String text, int startOffset, int endOffset) {
    return new TextChangeImpl(text, startOffset, endOffset);
  }
  
  private void insert(@NotNull String text, int offset) {
    myStorage.store(c(text, offset));
  }
  
  private void delete(int start, int end) {
    myStorage.store(c("", start, end));
  }

  private void replace(@NotNull String text, int start, int end) {
    myStorage.store(c(text, start, end));
  }
}
