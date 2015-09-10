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
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.Functions;
import com.intellij.util.PairFunction;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.openapi.util.Conditions.not;

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

  private static Map<Integer, Collection<Integer>> numbers2() {
    return ContainerUtil.<Integer, Collection<Integer>>immutableMapBuilder().
      put(1, Arrays.asList(2, 3, 4)).
      put(2, Arrays.asList(5, 6, 7)).
      put(3, Arrays.asList(8, 9, 10)).
      put(4, Arrays.asList(11, 12, 13)).
      put(5, Arrays.asList(14, 15, 16)).
      put(6, Arrays.asList(17, 18, 19)).
      put(7, Arrays.asList(20, 21, 22)).
      put(8, Arrays.asList(23, 24, 25)).
      put(9, Arrays.asList(26, 27, 28)).
      put(10, Arrays.asList(29, 30, 31)).
      put(11, Arrays.asList(32, 33, 34)).
      put(12, Arrays.asList(35, 36, 37)).
      build();
  }

  private static final Condition<Integer> IS_ODD = new Condition<Integer>() {
    @Override
    public boolean value(Integer integer) {
      return integer.intValue() % 2 == 1;
    }
  };

  private static Condition<Integer> inRange(final int s, final int e) {
    return new Condition<Integer>() {
      @Override
      public boolean value(Integer integer) {
        return s <= integer && integer <= e;
      }
    };
  }

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

  private static final Function<Integer, Integer> FIBONACCI2 = new JBIterable.StatefulTransform<Integer, Integer>() {
    int k0;
    @Override
    public Integer fun(Integer k) {
      int t = k0;
      k0 = k;
      return t + k;
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

  public void testAppend() {
    JBIterable<Integer> it = JBIterable.of(1, 2, 3).append(JBIterable.of(4, 5, 6)).append(7);
    assertEquals(7, it.size());
    assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7), it.toList());
    assertTrue(it.contains(5));
  }

  public void testGenerateRepeat() {
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).take(3).repeat(3);
    assertEquals(9, it.size());
    assertEquals(Arrays.asList(1, 2, 3, 1, 2, 3, 1, 2, 3), it.toList());
  }

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

  public void testOnce() {
    JBIterable<Integer> it = JBIterable.once(JBIterable.generate(1, INCREMENT).take(3).iterator());
    assertEquals(Arrays.asList(1, 2, 3), it.toList());
    try {
      assertEquals(Arrays.asList(1, 2, 3), it.toList());
      fail();
    }
    catch (UnsupportedOperationException ignored) {
    }
  }

  public void testStatefulFilter() {
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).take(5).filter(new JBIterable.StatefulFilter<Integer>() {
      int prev;
      @Override
      public boolean value(Integer integer) {
        boolean b = integer > prev;
        if (b) prev = integer;
        return b;
      }
    });
    assertEquals(Arrays.asList(1, 2, 3, 4, 5), it.toList());
    assertEquals(Arrays.asList(1, 2, 3, 4, 5), it.toList());
  }

  public void testStatefulGenerator() {
    JBIterable<Integer> it = JBIterable.generate(1, FIBONACCI2).take(8);
    assertEquals(Arrays.asList(1, 1, 2, 3, 5, 8, 13, 21), it.toList());
    assertEquals(Arrays.asList(1, 1, 2, 3, 5, 8, 13, 21), it.toList());
  }

  // TreeTraversal ----------------------------------------------

  @NotNull
  private static Function<Integer, JBIterable<Integer>> numTraverser(TreeTraversal t) {
    return t.traversal(Functions.fromMap(numbers()));
  }
  @NotNull
  private static Function<Integer, JBIterable<Integer>> numTraverser2(TreeTraversal t) {
    return t.traversal(Functions.fromMap(numbers2()));
  }

  public void testSimplePreOrderDfs() {
    assertEquals(Arrays.asList(1, 2, 5, 6, 7, 3, 8, 9, 10, 4, 11, 12, 13), numTraverser(TreeTraversal.PRE_ORDER_DFS).fun(1).toList());
  }

  public void testSimplePreOrderDfsBacktrace() {
    List<Integer> backDfs = Collections.emptyList();
    for (TreeTraversal.TracingIt<Integer> it = numTraverser2(TreeTraversal.PRE_ORDER_DFS).fun(1).typedIterator(); it.hasNext(); ) {
      if (it.next().equals(37)) backDfs = it.backtrace().toList();
    }
    List<Integer> backBfs = Collections.emptyList();
    for (TreeTraversal.TracingIt<Integer> it = numTraverser2(TreeTraversal.TRACING_BFS).fun(1).typedIterator(); it.hasNext(); ) {
      if (it.next().equals(37)) backBfs = it.backtrace().toList();
    }
    assertEquals(Arrays.asList(37, 12, 4, 1), backDfs);
    assertEquals(Arrays.asList(37, 12, 4, 1), backBfs);
  }

  public void testSimplePostOrderDfs() {
    assertEquals(Arrays.asList(5, 6, 7, 2, 8, 9, 10, 3, 11, 12, 13, 4, 1), numTraverser(TreeTraversal.POST_ORDER_DFS).fun(1).toList());
  }

  public void testSimpleBfs() {
    assertEquals(JBIterable.generate(1, INCREMENT).take(37).toList(), numTraverser2(TreeTraversal.PLAIN_BFS).fun(1).toList());
  }

  // GuidedTraversal ----------------------------------------------

  @NotNull
  private static Function.Mono<TreeTraversal.GuidedIt<Integer>> initGuide(@NotNull final TreeTraversal traversal) {
    return new Function.Mono<TreeTraversal.GuidedIt<Integer>>() {
      @Override
      public TreeTraversal.GuidedIt<Integer> fun(TreeTraversal.GuidedIt<Integer> it) {
        return it.setGuide(new Consumer<TreeTraversal.GuidedIt<Integer>>() {
          @Override
          public void consume(TreeTraversal.GuidedIt<Integer> it) {
            if (traversal == TreeTraversal.PRE_ORDER_DFS) {
              it.queueNext(it.curChild).result(it.curChild);
            }
            else if (traversal == TreeTraversal.POST_ORDER_DFS) {
              it.queueNext(it.curChild).result(it.curChild == null ? it.curParent : null);
            }
            else if (traversal == TreeTraversal.PLAIN_BFS) {
              it.queueLast(it.curChild).result(it.curChild);
            }
          }
        });
      }
    };
  }

  public void testGuidedDfs() {
    verifyGuidedTraversal(TreeTraversal.PRE_ORDER_DFS);
    verifyGuidedTraversal(TreeTraversal.POST_ORDER_DFS);
    verifyGuidedTraversal(TreeTraversal.PLAIN_BFS);
  }

  private static void verifyGuidedTraversal(TreeTraversal traversal) {
    assertEquals(numTraverser2(TreeTraversal.GUIDED_TRAVERSAL).fun(1).intercept(initGuide(traversal)).toList(),
                 numTraverser2(traversal).fun(1).toList());
  }


  // FilteredTraverser ----------------------------------------------

  @NotNull
  private static JBTreeTraverser<Integer> filteredTraverser() {
    return new JBTreeTraverser<Integer>(Functions.fromMap(numbers()));
  }

  public void testSimpleFilter() {
    JBTreeTraverser<Integer> t = filteredTraverser();
    assertEquals(Arrays.asList(1, 5, 7, 3, 9, 11, 13), t.withRoot(1).filter(IS_ODD).toList());
  }

  public void testSimpleExpand() {
    JBTreeTraverser<Integer> t = filteredTraverser();
    assertEquals(Arrays.asList(1, 2, 3, 8, 9, 10, 4), t.withRoot(1).expand(IS_ODD).toList());
  }

  public void testExpandFilter() {
    JBTreeTraverser<Integer> t = filteredTraverser();
    assertEquals(Arrays.asList(1, 3, 9), t.withRoot(1).expand(IS_ODD).filter(IS_ODD).toList());
  }

  public void testSkipExpandedDfs() {
    JBTreeTraverser<Integer> t = filteredTraverser();
    assertEquals(Arrays.asList(2, 8, 9, 10, 4), t.withRoot(1).expand(IS_ODD).traverse(TreeTraversal.LEAVES_DFS).toList());
  }

  public void testRangeChildrenLeavesDfs() {
    JBTreeTraverser<Integer> t = filteredTraverser();
    assertEquals(Arrays.asList(5, 6, 3, 11, 12, 13), t.withRoot(1).regard(not(inRange(7, 10))).traverse(TreeTraversal.LEAVES_DFS).toList());
  }

  public void testRangeChildrenLeavesBfs() {
    JBTreeTraverser<Integer> t = filteredTraverser();
    assertEquals(Arrays.asList(5, 6, 3, 11, 12, 13), t.withRoot(1).regard(not(inRange(7, 10))).traverse(TreeTraversal.LEAVES_DFS).toList());
  }

  public void testSkipExpandedBfs() {
    JBTreeTraverser<Integer> t = filteredTraverser();
    assertEquals(Arrays.asList(2, 4, 8, 9, 10), t.withRoot(1).expand(IS_ODD).traverse(TreeTraversal.LEAVES_BFS).toList());
  }

  public void testExpandSkipFilterReset() {
    JBTreeTraverser<Integer> t = filteredTraverser();
    assertEquals(Arrays.asList(1, 5, 7, 3, 9, 11, 13), t.withRoot(1).expand(IS_ODD).
      withTraversal(TreeTraversal.LEAVES_DFS).reset().filter(IS_ODD).toList());
  }

  public void testForceExlcudeReset() {
    JBTreeTraverser<Integer> t = filteredTraverser();
    assertEquals(Arrays.asList(1, 2, 6, 4, 12), t.withRoot(1).forceIgnore(IS_ODD).reset().toList());
  }

  public void testForceSkipReset() {
    JBTreeTraverser<Integer> t = filteredTraverser();
    assertEquals(Arrays.asList(1, 2, 6, 8, 10, 4, 12), t.withRoot(1).forceDisregard(IS_ODD).reset().toList());
  }

  public void testForceSkipLeavesDfs() {
    JBTreeTraverser<Integer> t = filteredTraverser();
    assertEquals(Arrays.asList(6, 8, 10, 12), t.withRoot(1).forceDisregard(IS_ODD).traverse(TreeTraversal.LEAVES_DFS).toList());
  }

  public void testFilterChildren() {
    JBTreeTraverser<Integer> t = filteredTraverser();
    assertEquals(Arrays.asList(1, 5, 7, 3, 9, 11, 13), t.withRoot(1).regard(IS_ODD).toList());
  }

  public void testEndlessGraph() {
    JBTreeTraverser<Integer> t = new JBTreeTraverser<Integer>(new Function<Integer, Iterable<Integer>>() {
      @Override
      public Iterable<Integer> fun(Integer k) {
        return JBIterable.generate(k, INCREMENT).transform(SQUARE).take(3);
      }
    });
    assertEquals(Arrays.asList(1, 1, 4, 9, 1, 4, 9, 16, 25, 36, 81), t.withRoot(1).bfsTraversal().take(11).toList());
  }

  public void testEndlessGraphParents() {
    JBTreeTraverser<Integer> t = new JBTreeTraverser<Integer>(new Function<Integer, Iterable<Integer>>() {
      @Override
      public Iterable<Integer> fun(Integer k) {
        return JBIterable.generate(1, k, FIBONACCI).skip(2).take(3);
      }
    });
    TreeTraversal.TracingIt<Integer> it = t.withRoot(1).preOrderDfsTraversal().skip(20).typedIterator();
    TreeTraversal.TracingIt<Integer> cursor = JBIterator.cursor(it).first();
    assertNotNull(cursor);
    assertSame(cursor, it);
    assertEquals(Arrays.asList(21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1), cursor.backtrace().toList());
  }

  public void testEdgeFilter() {
    JBTreeTraverser<Integer> t = filteredTraverser();
    JBIterable<Integer> it = t.regard(new FilteredTraverserBase.EdgeFilter<Integer>() {
      @Override
      public boolean value(Integer integer) {
        return (integer / curParent) % 2 == 0;
      }
    }).withRoot(1).traverse();
    assertEquals(Arrays.asList(1, 2, 5, 8, 10, 4, 11), it.toList());
    assertEquals(Arrays.asList(1, 2, 5, 8, 10, 4, 11), it.toList());
  }

  public void testStatefulChildFilter() {
    JBTreeTraverser<Integer> t = filteredTraverser();
    class F extends JBIterable.StatefulFilter<Integer> {
      int count;
      boolean value;
      F(boolean initialVal) { value = initialVal; }

      public boolean value(Integer integer) {
        return count ++ > 0 == value;
      }
    }

    JBIterable<Integer> it = t.regard(new F(true)).withRoot(1).traverse();
    assertEquals(Arrays.asList(1, 5, 6, 7, 3, 9, 10, 4, 12, 13), it.toList());
    assertEquals(Arrays.asList(1, 5, 6, 7, 3, 9, 10, 4, 12, 13), it.toList());
    assertEquals(it.toList(), t.forceDisregard(new F(false)).withRoot(1).reset().traverse().toList());
  }

}
