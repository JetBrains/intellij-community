/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.util.containers;

import com.intellij.openapi.util.Condition;
import com.intellij.util.Assertion;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class FilteringIteratorTest extends TestCase {
  public static final Condition STRINGS_ONLY = FilteringIterator.instanceOf(String.class);
  public static final Assertion CHECK = new Assertion();
  public static final Condition ANY = new Condition(){
          @Override
          public boolean value(Object object) {
            return true;
          }
        };

  public void testIteration() {
    Object[] values = new Object[]{"1", new Object(), "3", new Object()};
    Iterator iterator = FilteringIterator.create(Arrays.asList(values).iterator(), STRINGS_ONLY);
    CHECK.compareAll(new String[]{"1", "3"}, ContainerUtil.collect(iterator));
  }

  public void testRemove() {
    ArrayList collection = new ArrayList();
    collection.add(new Integer(1));
    collection.add("x");
    collection.add(new Integer(2));
    collection.add("2");
    collection.add("x");
    Object[] expected = new Object[]{new Integer(1), new Integer(2), "2"};
    checkRemove(expected, collection);
    collection.add(0, "x");
    checkRemove(expected, collection);
  }

  public void testIteratingNulls() {
    Object[] array = new Object[]{"1", null, "2", null};
    Iterator iterator = FilteringIterator.create(Arrays.asList(array).iterator(), ANY);
    CHECK.compareAll(array, ContainerUtil.collect(iterator));
  }

  public void testCallsHashNextOncePerElement() {
    ArrayList list = new ArrayList(Arrays.asList(new Object[]{null, "a", null, "b"}));
    MockIterator mockIterator = new MockIterator(list.iterator());
    MockCondition mockCondition = new MockCondition(STRINGS_ONLY);
    Iterator iterator = FilteringIterator.create(mockIterator, mockCondition);
    Assert.assertTrue(iterator.hasNext());
    Assert.assertTrue(iterator.hasNext());
    Assert.assertEquals(2, mockIterator.myHasNextCalls);
    Assert.assertEquals(2, mockCondition.myValueCalls);
    Assert.assertEquals("a", iterator.next());
    Assert.assertEquals(2, mockIterator.myHasNextCalls);
    Assert.assertEquals(2, mockCondition.myValueCalls);
    iterator.remove();
    Assert.assertEquals(2, mockIterator.myHasNextCalls);
    Assert.assertEquals(2, mockCondition.myValueCalls);
    Assert.assertTrue(iterator.hasNext());
    Assert.assertTrue(iterator.hasNext());
    Assert.assertEquals(4, mockIterator.myHasNextCalls);
    Assert.assertEquals(4, mockCondition.myValueCalls);
    Assert.assertEquals("b", iterator.next());
    Assert.assertEquals(4, mockIterator.myHasNextCalls);
    Assert.assertEquals(4, mockCondition.myValueCalls);

    Assert.assertEquals(3, list.size());
  }

  private void checkRemove(Object[] expected, ArrayList collection) {
    Iterator iterator = FilteringIterator.create(collection.iterator(), STRINGS_ONLY);
    while (iterator.hasNext()) {
      if (iterator.next().equals("x")) iterator.remove();
    }
    CHECK.compareAll(expected, collection);
  }

  private static class MockIterator implements Iterator {
    public int myHasNextCalls = 0;
    private final Iterator myIterator;

    public MockIterator(Iterator iterator) {
      myIterator = iterator;
    }

    @Override
    public boolean hasNext() {
      myHasNextCalls++;
      return myIterator.hasNext();
    }

    @Override
    public Object next() {
      return myIterator.next();
    }

    @Override
    public void remove() {
      myIterator.remove();
    }
  }

  private static class MockCondition implements Condition {
    private final Condition myCondition;
    public int myValueCalls = 0;

    public MockCondition(Condition condition) {
      myCondition = condition;
    }

    @Override
    public boolean value(Object o) {
      myValueCalls++;
      return myCondition.value(o);
    }
  }
}
