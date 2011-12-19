/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.openapi.util.Key;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ProcessingContext;

import java.util.Arrays;

import static com.intellij.patterns.StandardPatterns.*;

/**
 * @author peter
 */
public class StandardPatternsTest extends UsefulTestCase {

  public void testNull() throws Throwable {
    assertTrue(object().isNull().accepts(null));
    assertFalse(object().isNull().accepts(""));
    assertFalse(string().isNull().accepts(""));
    assertTrue(string().isNull().accepts(null));
  }

  public void testString() throws Throwable {
    final ElementPattern pattern = string();
    assertFalse(string().accepts(new Object()));
    assertTrue(pattern.accepts(""));
  }

  public void testStartsWith() throws Throwable {
    final ElementPattern pattern = string().startsWith("abc");
    assertFalse(pattern.accepts(""));
    assertTrue(pattern.accepts("abcd"));
    assertTrue(pattern.accepts("abc"));

    assertFalse(string().startsWith("abc").accepts(new Object()));
  }

  public void testEndsWith() throws Throwable {
    final ElementPattern pattern = string().endsWith("abc");
    assertFalse(pattern.accepts(""));
    assertFalse(pattern.accepts("abcd"));
    assertTrue(pattern.accepts("abc"));
  }

  private static void checkPrefixSuffix(final ElementPattern pattern) {
    assertFalse(pattern.accepts(""));
    assertFalse(pattern.accepts("abcd"));
    assertTrue(pattern.accepts("abc"));
    assertTrue(pattern.accepts("abcdabc"));
    assertFalse(pattern.accepts("abcdab"));
  }

  public void testPrefixSuffix() throws Throwable {
    checkPrefixSuffix(string().endsWith("abc").startsWith("abc"));
  }

  public void testAnd1() throws Throwable {
    checkPrefixSuffix(and(string().endsWith("abc"), string().startsWith("abc")));
  }

  public void testAnd2() throws Throwable {
    checkPrefixSuffix(string().endsWith("abc").and(string().startsWith("abc")));
  }

  public void testOr1() throws Throwable {
    checkOr(or(string().endsWith("abc"), string().startsWith("abc")));
  }

  public void testNot1() throws Throwable {
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

  public void testEquals() throws Throwable {
    final Object foo = new Object();
    final Object bar = new Object();
    ElementPattern objectPattern = object().equalTo(foo);
    assertTrue(objectPattern.accepts(foo));
    assertFalse(objectPattern.accepts(bar));

    ElementPattern stringPattern = string().equalTo("foo");
    assertTrue(stringPattern.accepts("foo"));
    assertFalse(stringPattern.accepts("bar"));
  }



  public void testAll() throws Throwable {
    ElementPattern pattern = collection(String.class).all(string().startsWith("abc"));
    assertTrue(pattern.accepts(Arrays.asList("abc")));
    assertTrue(pattern.accepts(Arrays.asList("abc", "abcd")));
    assertFalse(pattern.accepts(Arrays.asList("abc", "bcd")));
  }

  public void testAtLeastOne() throws Throwable {
    ElementPattern pattern = collection(String.class).atLeastOne(string().startsWith("abc"));
    assertTrue(pattern.accepts(Arrays.asList("abc")));
    assertTrue(pattern.accepts(Arrays.asList("abc", "abcd")));
    assertTrue(pattern.accepts(Arrays.asList("abc", "bcd")));
    assertFalse(pattern.accepts(Arrays.asList("bc", "bcd")));
    assertTrue(pattern.accepts(Arrays.asList("bc", "abc")));
  }

  public void testFilter() throws Throwable {
    ElementPattern pattern = collection(String.class).filter(string().endsWith("x"), collection(String.class).all(string().startsWith("abc")));
    assertTrue(pattern.accepts(Arrays.asList("abc")));
    assertTrue(pattern.accepts(Arrays.asList("abc", "abcd")));
    assertFalse(pattern.accepts(Arrays.asList("bcx", "bcd")));
    assertTrue(pattern.accepts(Arrays.asList("abcx", "abc")));
  }

  public void testFirst() throws Throwable {
    ElementPattern pattern = collection(String.class).first(string().startsWith("abc"));
    assertFalse(pattern.accepts(Arrays.<String>asList()));
    assertTrue(pattern.accepts(Arrays.asList("abc")));
    assertTrue(pattern.accepts(Arrays.asList("abc", "abcd")));
    assertTrue(pattern.accepts(Arrays.asList("abc", "bcd")));
    assertFalse(pattern.accepts(Arrays.asList("bc", "bcd")));
    assertFalse(pattern.accepts(Arrays.asList("bc", "abc")));
  }

  public void testLast() throws Throwable {
    //collection(String.class)

    ElementPattern pattern = collection(String.class).last(string().startsWith("abc"));
    assertFalse(pattern.accepts(Arrays.<String>asList()));
    assertTrue(pattern.accepts(Arrays.asList("abc")));
    assertTrue(pattern.accepts(Arrays.asList("abc", "abcd")));
    assertFalse(pattern.accepts(Arrays.asList("abc", "bcd")));
    assertFalse(pattern.accepts(Arrays.asList("bc", "bcd")));
    assertTrue(pattern.accepts(Arrays.asList("bc", "abc")));
  }

  public void testSize() throws Throwable {
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

  public void testEmpty() throws Throwable {
    final CollectionPattern<String> filter = collection(String.class);
    assertTrue(filter.empty().accepts(Arrays.<String>asList()));
    assertFalse(filter.empty().accepts(Arrays.asList("abc")));
    assertFalse(filter.empty().accepts(Arrays.asList("abc", "abc")));

    assertFalse(not(filter.empty()).accepts(Arrays.<String>asList()));
    assertTrue(not(filter.empty()).accepts(Arrays.asList("abc")));
    assertTrue(not(filter.empty()).accepts(Arrays.asList("abc", "abc")));
  }

  public void testSave() throws Throwable {
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
