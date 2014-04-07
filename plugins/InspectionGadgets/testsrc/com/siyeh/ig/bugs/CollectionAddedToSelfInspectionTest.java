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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class CollectionAddedToSelfInspectionTest extends LightInspectionTestCase {
  @Override
  protected InspectionProfileEntry getInspection() {
    return new CollectionAddedToSelfInspection();
  }

  public void testSimple() {
    doTest("import java.util.*;\n" +
           "\n" +
           "class CollectionAddedToSelf {\n" +
           "    private List foo = new ArrayList();\n" +
           "    private List bar = new ArrayList();" +
           "    private Map baz = new HashMap();\n" +
           "\n" +
           "    public void escher()\n" +
           "    {\n" +
           "        foo.add(/*'add()' called on collection 'foo' with itself as argument*/foo/**/);\n" +
           "        foo.set(0, /*'set()' called on collection 'foo' with itself as argument*/foo/**/);\n" +
           "        foo.add(bar);\n" +
           "        baz.put(/*'put()' called on collection 'baz' with itself as argument*/baz/**/, foo);\n" +
           "        baz.put(foo, /*'put()' called on collection 'baz' with itself as argument*/baz/**/);\n" +
           "        baz.put(foo, bar);\n" +
           "    }\n" +
           "}");
  }

  public void testSomeOtherMethods() {
    doTest("import java.util.*;\n" +
           "class X {" +
           "  void m(ArrayDeque ad) {" +
           "    ad.addFirst(/*'addFirst()' called on collection 'ad' with itself as argument*/ad/**/);" +
           "    ad.offerLast(/*'offerLast()' called on collection 'ad' with itself as argument*/ad/**/);" +
           "    ad.addLast(/*'addLast()' called on collection 'ad' with itself as argument*/ad/**/);" +
           "    ad.contains(/*'contains()' called on collection 'ad' with itself as argument*/ad/**/);" +
           "    ad.removeFirstOccurrence(/*'removeFirstOccurrence()' called on collection 'ad' with itself as argument*/ad/**/);" +
           "  }" +
           "}");
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util;" +
      "public interface Deque<E> extends Collection<E>{" +
      "  void addFirst(E e);" +
      "  void addLast(E e);" +
      "  boolean offerLast(E e);" +
      "  boolean contains(Object o);" +
      "  boolean removeFirstOccurrence(Object o);" +
      "}",
      "package java.util;" +
      "public abstract class ArrayDeque<E> implements Deque<E> {}"
    };
  }
}
