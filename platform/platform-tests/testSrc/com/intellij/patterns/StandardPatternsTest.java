/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.patterns;

import com.intellij.openapi.util.Key;
import com.intellij.util.ProcessingContext;
import junit.framework.TestCase;

import java.util.Arrays;

import static com.intellij.patterns.StandardPatterns.*;

/**
 * @author peter
 */
public class StandardPatternsTest extends TestCase {

  public void testNull() {
    assertTrue(object().isNull().accepts(null));
    assertFalse(object().isNull().accepts(""));
    assertFalse(string().isNull().accepts(""));
    assertTrue(string().isNull().accepts(null));
  }

  private static void checkPrefixSuffix(final ElementPattern pattern) {
    assertFalse(pattern.accepts(""));
    assertFalse(pattern.accepts("abcd"));
    assertTrue(pattern.accepts("abc"));
    assertTrue(pattern.accepts("abcdabc"));
    assertFalse(pattern.accepts("abcdab"));
  }

  public void testPrefixSuffix() {
    checkPrefixSuffix(string().endsWith("abc").startsWith("abc"));
  }

  public void testAnd1() {
    checkPrefixSuffix(and(string().endsWith("abc"), string().startsWith("abc")));
  }

  public void testAnd2() {
    checkPrefixSuffix(string().endsWith("abc").and(string().startsWith("abc")));
  }

  public void testOr1() {
    checkOr(or(string().endsWith("abc"), string().startsWith("abc")));
  }

  public void testNot1() {
    final ElementPattern pattern = not(or(string().endsWith("abc"), string().startsWith("abc")));
    assertTrue(pattern.accepts(""));
    assertTrue(pattern.accepts("xxx"));
    assertFalse(pattern.accepts("abcd"));
    assertTrue(pattern.accepts("dabcd"));
    assertFalse(pattern.accepts("abc"));
    assertFalse(pattern.accepts("abcdabc"));
    assertFalse(pattern.accepts("abcdab"));
  }

  private static void checkOr(final ElementPattern filterFactory) {
    assertFalse(filterFactory.accepts(""));
    assertFalse(filterFactory.accepts("xxx"));
    assertTrue(filterFactory.accepts("abcd"));
    assertFalse(filterFactory.accepts("dabcd"));
    assertTrue(filterFactory.accepts("abc"));
    assertTrue(filterFactory.accepts("abcdabc"));
    assertTrue(filterFactory.accepts("abcdab"));
  }

  public void testEquals() {
    final Object foo = new Object();
    final Object bar = new Object();
    ElementPattern objectPattern = object().equalTo(foo);
    assertTrue(objectPattern.accepts(foo));
    assertFalse(objectPattern.accepts(bar));

    ElementPattern stringPattern = string().equalTo("foo");
    assertTrue(stringPattern.accepts("foo"));
    assertFalse(stringPattern.accepts("bar"));
  }


  public void testAll() {
    ElementPattern pattern = collection(String.class).all(string().startsWith("abc"));
    assertTrue(pattern.accepts(Arrays.asList("abc")));
    assertTrue(pattern.accepts(Arrays.asList("abc", "abcd")));
    assertFalse(pattern.accepts(Arrays.asList("abc", "bcd")));
  }

  public void testAtLeastOne() {
    ElementPattern pattern = collection(String.class).atLeastOne(string().startsWith("abc"));
    assertTrue(pattern.accepts(Arrays.asList("abc")));
    assertTrue(pattern.accepts(Arrays.asList("abc", "abcd")));
    assertTrue(pattern.accepts(Arrays.asList("abc", "bcd")));
    assertFalse(pattern.accepts(Arrays.asList("bc", "bcd")));
    assertTrue(pattern.accepts(Arrays.asList("bc", "abc")));
  }

  public void testFilter() {
    ElementPattern pattern =
      collection(String.class).filter(string().endsWith("x"), collection(String.class).all(string().startsWith("abc")));
    assertTrue(pattern.accepts(Arrays.asList("abc")));
    assertTrue(pattern.accepts(Arrays.asList("abc", "abcd")));
    assertFalse(pattern.accepts(Arrays.asList("bcx", "bcd")));
    assertTrue(pattern.accepts(Arrays.asList("abcx", "abc")));
  }

  public void testFirst() {
    ElementPattern pattern = collection(String.class).first(string().startsWith("abc"));
    assertFalse(pattern.accepts(Arrays.<String>asList()));
    assertTrue(pattern.accepts(Arrays.asList("abc")));
    assertTrue(pattern.accepts(Arrays.asList("abc", "abcd")));
    assertTrue(pattern.accepts(Arrays.asList("abc", "bcd")));
    assertFalse(pattern.accepts(Arrays.asList("bc", "bcd")));
    assertFalse(pattern.accepts(Arrays.asList("bc", "abc")));
  }

  public void testLast() {
    //collection(String.class)

    ElementPattern pattern = collection(String.class).last(string().startsWith("abc"));
    assertFalse(pattern.accepts(Arrays.<String>asList()));
    assertTrue(pattern.accepts(Arrays.asList("abc")));
    assertTrue(pattern.accepts(Arrays.asList("abc", "abcd")));
    assertFalse(pattern.accepts(Arrays.asList("abc", "bcd")));
    assertFalse(pattern.accepts(Arrays.asList("bc", "bcd")));
    assertTrue(pattern.accepts(Arrays.asList("bc", "abc")));
  }

  public void testSize() {
    final CollectionPattern<String> filter = collection(String.class);
    assertTrue(filter.size(0).accepts(Arrays.<String>asList()));
    assertFalse(filter.size(0).accepts(Arrays.asList("abc")));
    assertFalse(filter.size(0).accepts(Arrays.asList("abc", "abc")));

    assertFalse(filter.size(1).accepts(Arrays.<String>asList()));
    assertTrue(filter.size(1).accepts(Arrays.asList("abc")));
    assertFalse(filter.size(1).accepts(Arrays.asList("abc", "abc")));

    assertFalse(filter.size(2).accepts(Arrays.<String>asList()));
    assertFalse(filter.size(2).accepts(Arrays.asList("abc")));
    assertTrue(filter.size(2).accepts(Arrays.asList("abc", "abc")));
  }

  public void testEmpty() {
    final CollectionPattern<String> filter = collection(String.class);
    assertTrue(filter.empty().accepts(Arrays.<String>asList()));
    assertFalse(filter.empty().accepts(Arrays.asList("abc")));
    assertFalse(filter.empty().accepts(Arrays.asList("abc", "abc")));

    assertFalse(not(filter.empty()).accepts(Arrays.<String>asList()));
    assertTrue(not(filter.empty()).accepts(Arrays.asList("abc")));
    assertTrue(not(filter.empty()).accepts(Arrays.asList("abc", "abc")));
  }

  public void testSave() {
    Key<String> key = Key.create("abc");
    final ProcessingContext context = new ProcessingContext();
    assertFalse(string().contains("abc").save(key).accepts(null));
    assertNull(context.get(key));

    assertFalse(string().contains("abc").save(key).accepts("def"));
    assertNull(context.get(key));

    final String s = "defabcdef";
    assertTrue(string().contains("abc").save(key).accepts(s, context));
    assertSame(s, context.get(key));
  }
}
