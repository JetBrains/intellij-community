/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import junit.framework.TestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class IteratorNextDoesNotThrowNoSuchElementExceptionInspectionTest extends LightInspectionTestCase {

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

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new IteratorNextDoesNotThrowNoSuchElementExceptionInspection();
  }
}