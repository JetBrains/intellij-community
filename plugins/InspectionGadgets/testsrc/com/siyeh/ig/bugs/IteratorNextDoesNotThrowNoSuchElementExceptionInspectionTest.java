// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class IteratorNextDoesNotThrowNoSuchElementExceptionInspectionTest extends LightJavaInspectionTestCase {

  public void testPrevious() {
    doTest("import java.util.*;" +
           "class ReverseListIterator<T> implements Iterator<T> {" +
           "    private ListIterator<T> iterator;" +
           "    public ReverseListIterator(List<T> list) {" +
           "        this.iterator = list.listIterator(list.size());" +
           "    }" +
           "    public boolean hasNext() {" +
           "        return iterator.hasPrevious();" +
           "    }" +
           "    public T next() {" +
           "        return iterator.previous();" +
           "    }" +
           "    public void remove() {" +
           "        iterator.remove();" +
           "    }" +
           "}");
  }

  public void testEmpty() {
    doTest("import java.util.*;" +
           "class EmptyIterator<T> implements Iterator<T> {" +
           "    public boolean hasNext() {" +
           "        return false;" +
           "    }" +
           "    public T /*'Iterator.next()' which can't throw 'NoSuchElementException'*/next/**/() {" +
           "        return null;" +
           "    }" +
           "    public void remove() {" +
           "    }" +
           "}");
  }

  public void testCompiledMethodCall() {
    doTest("import java.util.*;\n" +
           "\n" +
           "class MyIterator implements Iterator<String> {\n" +
           "  @Override\n" +
           "  public boolean hasNext() {\n" +
           "    return true;\n" +
           "  }\n" +
           "\n" +
           "  @Override\n" +
           "  public String /*'Iterator.next()' which can't throw 'NoSuchElementException'*/next/**/() {\n" +
           "    return \"xyz\".trim();\n" + // cannot analyze compiled library method
           "  }\n" +
           "  \n" +
           "  public void remove() {}\n" +
           "}\n");
  }

  public void testEnumeration() {
    doTest("import java.util.*;" +
           "class EnumerationIterator<T> implements Iterator<T> {" +
           "  Enumeration<T> myEnumeration;" +
           "  EnumerationIterator(Enumeration<T> enumeration) {" +
           "    myEnumeration = enumeration;" +
           "  }" +
           "  public boolean hasNext() {" +
           "    return myEnumeration.hasMoreElements();" +
           "  }" +
           "  public T next() {" +
           "    return myEnumeration.nextElement();" +
           "  }" +
           "  public void remove() {}" +
           "}");
  }

  public void testInsideAnonymous() {
    doTest("import java.util.*;" +
           "class A<T> {{" +
           "Iterator<T> i = new Iterator<>() {" +
           "  Enumeration<T> myEnumeration;" +
           "  public boolean hasNext() {" +
           "    return myEnumeration.hasMoreElements();" +
           "  }" +
           "  public T next() {" +
           "    if (this.hasNext()) {\n" +
           "      return null;\n" +
           "    }\n" +
           "    throw new NoSuchElementException();" +
           "  }" +
           "  public void remove() {}" +
           "};" +
           "}}");
  }

  public void testAbstractMethod() {
    doTest("import java.util.Iterator;\n" +
           "interface MyIterator extends Iterator<String> {\n" +
           "    @Override\n" +
           "    String next();\n" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new IteratorNextDoesNotThrowNoSuchElementExceptionInspection();
  }
}