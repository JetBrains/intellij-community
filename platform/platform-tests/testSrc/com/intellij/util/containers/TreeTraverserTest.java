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
package com.intellij.util.containers;

import com.intellij.openapi.util.Condition;
import com.intellij.util.Function;
import com.intellij.util.Functions;
import com.intellij.util.PairFunction;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * @author gregsh
 */
public class TreeTraverserTest extends TestCase {

  private static Map<Integer, Collection<Integer>> numbers() {
    return ContainerUtil.<Integer, Collection<Integer>>immutableMapBuilder().
      put(1, Arrays.asList(2, 3, 4)).
      put(2, Arrays.asList(5, 6, 7)).
      put(3, Arrays.asList(8, 9, 10)).
      put(4, Arrays.asList(11, 12, 13)).
      build();
  }

  private static final Condition<Integer> IS_ODD = new Condition<Integer>() {
    @Override
    public boolean value(Integer integer) {
      return integer.intValue() % 2 == 1;
    }
  };

  private static final Function<Integer, Integer> INCREMENT = new Function<Integer, Integer>() {
    @Override
    public Integer fun(Integer k) {
      return k + 1;
    }
  };

  private static final Function<Integer, Integer> SQUARE = new Function<Integer, Integer>() {
    @Override
    public Integer fun(Integer k) {
      return k * k;
    }
  };

  private static final PairFunction<Integer, Integer, Integer> FIBONACCI = new PairFunction<Integer, Integer, Integer>() {
    @Override
    public Integer fun(Integer k1, Integer k2) {
      return k2 + k1;
    }
  };

  @NotNull
  private static Condition<Integer> LESS_THAN(final int max) {
    return new Condition<Integer>() {
      @Override
      public boolean value(Integer integer) {
        return integer < max;
      }
    };
  }

  @NotNull
  private static Condition<Integer> LESS_THAN_MOD(final int max) {
    return new Condition<Integer>() {
      @Override
      public boolean value(Integer integer) {
        return integer % max < max / 2;
      }
    };
  }

  // JBIterable ----------------------------------------------

  public void testSkipTakeSize() {
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).skip(10).take(10);
    assertEquals(10, it.size());
    assertEquals(new Integer(11), it.first());
  }

  public void testSkipWhile() {
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).skipWhile(LESS_THAN_MOD(10)).take(10);
    assertEquals(Arrays.asList(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), it.toList());
  }

  public void testTakeWhile() {
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).takeWhile(LESS_THAN_MOD(10)).take(10);
    assertEquals(Arrays.asList(1, 2, 3, 4), it.toList());
  }

  public void testFilterTransformTakeWhile() {
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).filter(IS_ODD).transform(SQUARE).takeWhile(LESS_THAN(100));
    assertEquals(Arrays.asList(1, 9, 25, 49, 81), it.toList());
    assertEquals(new Integer(1), it.first());
    assertEquals(new Integer(81), it.last());
  }

  public void testFilterTransformSkipWhile() {
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).filter(IS_ODD).transform(SQUARE).skipWhile(LESS_THAN(100)).take(3);
    assertEquals(Arrays.asList(121, 169, 225), it.toList());
    assertEquals(new Integer(121), it.first());
    assertEquals(new Integer(225), it.last());
  }

  // TreeTraverser ----------------------------------------------

  @NotNull
  private static TreeTraverser<Integer> traverser() {
    return new TreeTraverser<Integer>(Functions.fromMap(numbers()));
  }

  public void testSimplePreOrderDfs() {
    TreeTraverser<Integer> t = traverser();
    assertEquals(Arrays.asList(1, 2, 5, 6, 7, 3, 8, 9, 10, 4, 11, 12, 13), t.preOrderTraversal(1).toList());
  }

  public void testSimplePostOrderDfs() {
    TreeTraverser<Integer> t = traverser();
    assertEquals(Arrays.asList(5, 6, 7, 2, 8, 9, 10, 3, 11, 12, 13, 4, 1), t.postOrderTraversal(1).toList());
  }

  public void testSimpleBfs() {
    TreeTraverser<Integer> t = traverser();
    assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13), t.breadthFirstTraversal(1).toList());
  }

  // FilteredTraverser ----------------------------------------------

  @NotNull
  private static FilteredTraverser<Integer> filteredTraverser() {
    return new FilteredTraverser<Integer>(Functions.fromMap(numbers()));
  }

  public void testSimpleFilter() {
    FilteredTraverser<Integer> t = filteredTraverser();
    assertEquals(Arrays.asList(1, 5, 7, 3, 9, 11, 13), t.withRoot(1).filter(IS_ODD).toList());
  }

  public void testSimpleExpand() {
    FilteredTraverser<Integer> t = filteredTraverser();
    assertEquals(Arrays.asList(1, 2, 3, 8, 9, 10, 4), t.withRoot(1).expand(IS_ODD).toList());
  }

  public void testExpandFilter() {
    FilteredTraverser<Integer> t = filteredTraverser();
    assertEquals(Arrays.asList(1, 3, 9), t.withRoot(1).expand(IS_ODD).filter(IS_ODD).toList());
  }

  public void testExpandSkipFilter() {
    FilteredTraverser<Integer> t = filteredTraverser();
    // note: 9, 11 ..etc are expanded
    assertEquals(Arrays.asList(2, 8, 10, 4), t.withRoot(1).expand(IS_ODD).skipExpanded(true).toList());
  }

  public void testExpandSkipFilterReset() {
    FilteredTraverser<Integer> t = filteredTraverser();
    // note: reset clears skipExpanded
    assertEquals(Arrays.asList(1, 5, 7, 3, 9, 11, 13), t.withRoot(1).expand(IS_ODD).
      skipExpanded(true).reset().filter(IS_ODD).toList());
  }

  public void testEndlessGraph() {
    FilteredTraverser<Integer> t = new FilteredTraverser<Integer>(new Function<Integer, Iterable<Integer>>() {
      @Override
      public Iterable<Integer> fun(Integer k) {
        return JBIterable.generate(k, INCREMENT).transform(SQUARE).take(3);
      }
    });
    assertEquals(Arrays.asList(1, 1, 4, 9, 1, 4, 9, 16, 25, 36, 81), t.withRoot(1).breadthFirstTraversal().take(11).toList());
  }

  public void testEndlessGraphParents() {
    FilteredTraverser<Integer> t = new FilteredTraverser<Integer>(new Function<Integer, Iterable<Integer>>() {
      @Override
      public Iterable<Integer> fun(Integer k) {
        return JBIterable.generate(1, k, FIBONACCI).skip(2).take(3);
      }
    });
    TreeTraverser.TracingIt<Integer> it = t.withRoot(1).preOrderTraversal().skip(20).typedIterator();
    TreeTraverser.TracingIt<Integer> cursor = JBIterator.cursor(it).first();
    assertNotNull(cursor);
    assertSame(cursor, it);
    assertEquals(Arrays.asList(20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1), cursor.backtrace().toList());
  }

}
