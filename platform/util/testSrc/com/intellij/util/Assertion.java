// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util;

import com.intellij.openapi.util.Comparing;
import gnu.trove.Equality;
import junit.framework.AssertionFailedError;
import org.junit.Assert;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class Assertion extends Assert {
  private StringConvertion myStringConvertion;
  private Equality myEquality = Equality.CANONICAL;

  public Assertion() {
    this(StringConvertion.DEFAULT);
  }

  public Assertion(StringConvertion stringConvertion) {
    myStringConvertion = stringConvertion;
  }

  public void setStringConvertion(StringConvertion stringConvertion) {
    myStringConvertion = stringConvertion;
  }

  public StringConvertion getStringConvertion() { return myStringConvertion; }

  public Equality getEquality() { return myEquality; }

  public void compareAll(Object[] expected, Object[] actual) {
    checkNotNulls(expected, actual);
    String expectedLines = converToLines(expected);
    String actualLines = converToLines(actual);
    Assert.assertEquals(expectedLines, actualLines);
    Assert.assertEquals(expected.length, actual.length);
    for (int i = 0; i < expected.length; i++) {
      checkEquals("Index=" + i, expected[i], actual[i]);
    }
  }

  private static void checkNotNulls(Object[] expected, Object[] actual) {
    Assert.assertNotNull("Expected is null", expected);
    Assert.assertNotNull("Actual is null", actual);
  }

  public void compareAll(Object[][] expected, Object[][] actual) {
    checkNotNulls(expected, actual);
    Assert.assertEquals(convertToLines(expected), convertToLines(actual));
    Assert.assertEquals(expected.length, actual.length);
    for (int i = 0; i < expected.length; i++) {
      compareAll(expected[i], actual[i]);
    }
  }

  private String convertToLines(Object[][] expected) {
    StringBuilder expectedLines = new StringBuilder();
    for (Object[] objects : expected) {
      expectedLines.append(concatenateAsStrings(objects, " "));
      expectedLines.append("\n");
    }
    return expectedLines.toString();
  }

  private void checkEquals(String message, Object expected, Object actual) {
    Assert.assertTrue(message +
               " expected:<" + convertToString(expected) +
               "> actual:" + convertToString(actual) + ">",
               myEquality.equals(expected, actual));
  }

  public String converToLines(Object[] objects) {
    return concatenateAsStrings(objects, "\n");
  }

  private String concatenateAsStrings(Object[] objects, String separator) {
    StringBuilder buffer = new StringBuilder();
    String lineEnd = "";
    for (Object object : objects) {
      buffer.append(lineEnd);
      buffer.append(convertToString(object));
      lineEnd = separator;
    }
    return buffer.toString();
  }

  public void enumerate(Object[] objects) {
    for (int i = 0; i < objects.length; i++) {
      Object object = objects[i];
      System.out.println("[" + i + "] = " + convertToString(object));
    }
  }

  public void enumerate(Collection objects) {
    enumerate(objects.toArray());
  }

  private String convertToString(Object object) {
    if (object == null) return "null";
    return myStringConvertion.convert(object);
  }

  public void compareAll(Object[] expected, List actual) {
    compareAll(expected, actual.toArray());
  }

  public void compareAll(List expected, Object[] actual) {
    compareAll(expected.toArray(), actual);
  }

  public static void compareUnordered(Object[] expected, Collection actual) {
    assertEquals(String.format("Collections have different sizes%nExpected: %s%n Actual: %s%n", Arrays.toString(expected), actual),
                 expected.length, actual.size());
    for (Object exp : expected) {
      assertTrue(String.format("Expected element %s was not found in the collection %s", exp, actual), actual.contains(exp));
    }
  }

  public void compareAll(List expected, List actual) {
    compareAll(expected, actual.toArray());
  }

  public static void compareLines(String text, String[] lines) throws IOException {
    BufferedReader reader = new BufferedReader(new StringReader(text));
    for (int i = 0; i < lines.length - 1; i++)
      Assert.assertEquals(lines[i], reader.readLine());
    String lastLine = lines[lines.length - 1];
    char[] buffer = new char[lastLine.length()];
    reader.read(buffer, 0, buffer.length);
    Assert.assertEquals(lastLine, new String(buffer));
    Assert.assertEquals(-1, reader.read());
  }

  public void contains(Collection collection, Object object) {
    if (collection.contains(object))
      return;
    compareAll(new Object[]{object}, collection.toArray());
    Assert.assertTrue(collection.contains(object));
  }

  public void contains(Object[] array, Object object) {
    contains(Arrays.asList(array), object);
  }

  public void singleElement(Collection collection, Object object) {
    compareAll(new Object[]{object}, collection.toArray());
    Assert.assertEquals(1, collection.size());
    checkEquals("", object, collection.iterator().next());
  }

  public void empty(Object[] array) {
    try {
      compareAll(ArrayUtil.EMPTY_OBJECT_ARRAY, array);
    } catch(AssertionFailedError e) {
      System.err.println("Size: " + array.length);
      throw e;
    }
  }

  public void empty(Collection objects) {
    empty(objects.toArray());
  }

  public void count(int count, Collection objects) {
    if (count != objects.size()) {
      empty(objects);
    }
    Assert.assertEquals(count, objects.size());
  }

  public void singleElement(Object[] objects, Object element) {
    singleElement(Arrays.asList(objects), element);
  }

  public void count(int number, Object[] objects) {
    count(number, Arrays.asList(objects));
  }

  public static void compareUnordered(Object[] expected, Object[] actual) {
    compareUnordered(expected, new HashSet(Arrays.asList(actual)));
  }

  public void compareAll(int[] expected, int[] actual) {
    compareAll(asObjectArray(expected), asObjectArray(actual));
  }

  private static Object[] asObjectArray(int[] ints) {
    Object[] result = new Object[ints.length];
    for (int i = 0; i < ints.length; i++) {
      int anInt = ints[i];
      result[i] = Integer.valueOf(anInt);
    }
    return result;
  }

  public void setEquality(Equality equality) {
    myEquality = equality;
  }

  public void singleElement(int[] actual, int element) {
    compareAll(new int[]{element}, actual);
  }

  public static void size(int size, Collection collection) {
    if (collection.size() != size) {
      System.err.println("Expected: " + size + " actual: " + collection.size());
    }
    Assert.assertEquals(size, collection.size());
  }

  public void containsAll(Object[] array, Collection subCollection) {
    containsAll(Arrays.asList(array), subCollection);
  }

  public void containsAll(Collection list, Collection subCollection) {
    if (list.containsAll(subCollection)) return;
    for (Object item : subCollection) {
      boolean isContained = false;
      for (Object superSetItem : list) {
        if (myEquality.equals(superSetItem, item)) {
          isContained = true;
          break;
        }
      }
      Assert.assertTrue(myStringConvertion.convert(item), isContained);
    }
  }

  public <T> void singleOccurence(Collection<T> collection, T item) {
    int number = countOccurences(collection, item);
    if (number != 1) {
      enumerate(collection);
      Assert.fail(myStringConvertion.convert(item) + "\n occured " + number + " times");
    }
  }

  public static <T> int countOccurences(Collection<T> collection, T item) {
    int counter = 0;
    for (T obj : collection) {
      if (Comparing.equal(item, obj)) counter++;
    }
    return counter;
  }

  public void containsAll(Collection collection, Object[] subArray) {
    containsAll(collection, Arrays.asList(subArray));
  }

  public static void size(int size, Object[] objects) {
    size(size, Arrays.asList(objects));
  }

  public void containsAll(Object[] array, Object[] subArray) {
    containsAll(array, Arrays.asList(subArray));
  }
}
