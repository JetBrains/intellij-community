/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import static com.intellij.openapi.util.Conditions.not;

/**
 * A redesigned version of com.google.common.collect.TreeTraversal.
 * <p/>
 * The original JavaDoc:
 * <p/>
 * Views elements of a type {@code T} as nodes in a tree, and provides methods to traverse the trees
 * induced by this traverser.
 *
 * <p>For example, the tree
 *
 * <pre>{@code
 *          h
 *        / | \
 *       /  e  \
 *      d       g
 *     /|\      |
 *    / | \     f
 *   a  b  c       }</pre>
 *
 * <p>can be iterated over in pre-order (hdabcegf), post-order (abcdefgh), or breadth-first order
 * (hdegabcf).
 *
 * <p>Null nodes are strictly forbidden.
 *
 * @author Louis Wasserman
 * <p/>
 *
 * @author gregsh
 */
public abstract class TreeTraversal {

  private final String debugName;

  protected TreeTraversal(@NotNull String debugName) {
    this.debugName = debugName;
  }

  @NotNull
  public final <T> JBIterable<T> traversal(@NotNull final Iterable<? extends T> roots, @NotNull final Function<T, ? extends Iterable<? extends T>> tree) {
    return new JBIterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        return createIterator(roots, tree);
      }
    };
  }

  @NotNull
  public final <T> JBIterable<T> traversal(@Nullable final T root, @NotNull final Function<T, ? extends Iterable<? extends T>> tree) {
    return traversal(ContainerUtil.createMaybeSingletonList(root), tree);
  }

  @NotNull
  public final <T> Function<T, JBIterable<T>> traversal(@NotNull final Function<T, ? extends Iterable<? extends T>> tree) {
    return new Function<T, JBIterable<T>>() {
      @Override
      public JBIterable<T> fun(T t) {
        return traversal(t, tree);
      }
    };
  }

  /**
   * Configures the traversal to skip already visited nodes.
   * @see TreeTraversal#unique(Function)
   */
  @NotNull
  public final TreeTraversal unique() {
    return unique(Function.ID);
  }

  /**
   * Configures the traversal to skip already visited nodes.
   * @param identity function
   */
  @NotNull
  public TreeTraversal unique(@NotNull final Function<?, ?> identity) {
    final TreeTraversal original = this;
    return new TreeTraversal(debugName + " (UNIQUE)") {

      @NotNull
      @Override
      public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots,
                                      @NotNull final Function<T, ? extends Iterable<? extends T>> tree) {
        class WrappedTree implements Condition<T>, Function<T, Iterable<? extends T>> {
          HashSet<Object> visited;

          @Override
          public boolean value(T e) {
            if (visited == null) visited = new HashSet<Object>();
            //noinspection unchecked
            return visited.add(((Function<T, Object>)identity).fun(e));
          }

          @Override
          public Iterable<? extends T> fun(T t) {
            return JBIterable.from(tree.fun(t)).filter(this);
          }
        }
        if (tree instanceof WrappedTree) return original.createIterator(roots, tree);
        WrappedTree wrappedTree = new WrappedTree();
        return original.createIterator(JBIterable.from(roots).filter(wrappedTree), wrappedTree);
      }
    };
  }

  /**
   * Configures the traversal to expand and return the nodes within the range only.
   * It is an optimized version of expand-and-filter operation.
   * It skips all the nodes "before" the {@code rangeCondition} return true for the first time,
   * processes as usual the nodes while the condition return true and
   * stops when the {@code rangeCondition} return false after that.
   */
  @NotNull
  public TreeTraversal onRange(@NotNull final Condition<?> rangeCondition) {
    final TreeTraversal original = this;
    return new TreeTraversal(original.toString() + " (ON_RANGE)") {
      @NotNull
      @Override
      public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots,
                                      @NotNull final Function<T, ? extends Iterable<? extends T>> tree) {
        final Condition<? super T> inRangeCondition = (Condition < ? super T >)rangeCondition;
        final Condition<? super T> notInRangeCondition = (Condition<? super T>)not(rangeCondition);
        class WrappedTree implements Function<T, Iterable<? extends T>> {
          @Override
          public Iterable<? extends T> fun(T t) {
            return JBIterable.from(tree.fun(t))
              .skipWhile(notInRangeCondition)
              .takeWhile(inRangeCondition);
          }
        }
        if (tree instanceof WrappedTree) return original.createIterator(roots, tree);
        WrappedTree wrappedTree = new WrappedTree();
        return original.createIterator(JBIterable.from(roots).filter(inRangeCondition), wrappedTree);
      }
    };
  }

  /**
   * Creates a new iterator for this type of traversal.
   * @param roots tree roots
   * @param tree tree structure the children for parent function.
   *             May return null (useful for map representation).
   */
  @NotNull
  public abstract <T> It<T> createIterator(@NotNull Iterable<? extends T> roots, @NotNull Function<T, ? extends Iterable<? extends T>> tree);

  @Override
  public final String toString() {
    return debugName;
  }

  public static abstract class It<T> extends JBIterator<T> {
    protected final Function<T, ? extends Iterable<? extends T>> tree;

    protected It(Function<T, ? extends Iterable<? extends T>> tree) {
      this.tree = tree;
    }
  }

  public static abstract class TracingIt<T> extends It<T> {

    @Nullable
    public T parent() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    public JBIterable<T> backtrace() {
      throw new UnsupportedOperationException();
    }

    protected TracingIt(Function<T, ? extends Iterable<? extends T>> tree) {
      super(tree);
    }

    protected JBIterable<T> _transform(JBIterable<?> original) {
      JBIterable<?> result = original;
      for (Function<Object, Object> f : getTransformations()) {
        result = result.transform(f);
      }
      //noinspection unchecked
      return (JBIterable<T>)result;
    }

    protected T _transform(Object original) {
      Object result = original;
      for (Function<Object, ?> f : getTransformations()) {
        result = f.fun(result);
      }
      //noinspection unchecked
      return (T)result;
    }
  }

  public static abstract class GuidedIt<T> extends It<T> {

    public interface Guide<T> {
      void guide(@NotNull GuidedIt<T> guidedIt);
    }

    @Nullable
    public T curChild, curParent;
    @Nullable
    public Iterable<? extends T> curChildren;
    public boolean curNoChildren;

    public abstract GuidedIt<T> queueNext(@Nullable T child);
    public abstract GuidedIt<T> result(@Nullable T node);
    public abstract GuidedIt<T> queueLast(@Nullable T child);

    protected GuidedIt(Function<T, ? extends Iterable<? extends T>> tree) {
      super(tree);
    }
  }

  @NotNull
  public static TreeTraversal GUIDED_TRAVERSAL(@NotNull final GuidedIt.Guide<?> guide) {
    return new TreeTraversal("GUIDED_TRAVERSAL") {
      @NotNull
      @Override
      public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots, @NotNull Function<T, ? extends Iterable<? extends T>> tree) {
        //noinspection unchecked
        return new GuidedItImpl<T>(roots, tree, (GuidedIt.Guide<T>)guide);
      }
    };
  }

  /**
   * Returns an iterator over the nodes in a tree structure, using pre-order traversal.
   * That is, each node's subtrees are traversed after the node itself is returned.
   *
   * <p>No guarantees are made about the behavior of the traversal when nodes change while
   * iteration is in progress or when the iterators generated by {@code tree} are advanced.
   */
  @NotNull
  public static final TreeTraversal PRE_ORDER_DFS = new TreeTraversal("PRE_ORDER_DFS") {
    @NotNull
    @Override
    public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots, @NotNull Function<T, ? extends Iterable<? extends T>> tree) {
      return new PreOrderIt<T>(roots, tree);
    }
  };

  /**
   * Returns an iterator over the nodes in a tree structure, using post-order DFS traversal.
   * That is, each node's subtrees are traversed before the node itself is returned.
   * <p/>
   * <p>No guarantees are made about the behavior of the traversal when nodes change while
   * iteration is in progress or when the iterators generated by {@code tree} are advanced.
   */
  @NotNull
  public static final TreeTraversal POST_ORDER_DFS = new TreeTraversal("POST_ORDER_DFS") {
    @NotNull
    @Override
    public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots, @NotNull Function<T, ? extends Iterable<? extends T>> tree) {
      return new PostOrderIt<T>(roots, tree);
    }
  };


  /**
   * Returns an iterator over the leaf nodes only in a tree structure, using DFS traversal.
   * That is, each node's subtrees are traversed before the node itself is returned.
   */
  @NotNull
  public static final TreeTraversal LEAVES_DFS = new TreeTraversal("LEAVES_DFS") {
    @NotNull
    @Override
    public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots, @NotNull Function<T, ? extends Iterable<? extends T>> tree) {
      return new LeavesDfsIt<T>(roots, tree);
    }
  };

  /**
   * Returns an iterator over the nodes in a tree structure, using interlaced pre-order DFS traversal.
   * That is, all paths are traversed in an interlaced manner that is suitable for infinite and cyclic graphs
   * and each node's subtrees are traversed before the node itself is returned.
   * <p/>
   * <p>No guarantees are made about the behavior of the traversal when nodes change while
   * iteration is in progress or when the iterators generated by {@code tree} are advanced.
   */
  @NotNull
  public static final TreeTraversal INTERLEAVED_DFS = new TreeTraversal("INTERLEAVED_DFS") {
    @NotNull
    @Override
    public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots, @NotNull Function<T, ? extends Iterable<? extends T>> tree) {
      return new InterleavedIt<T>(roots, tree);
    }
  };

  /**
   * Returns an iterator over the nodes in a tree structure, using breadth-first traversal.
   * That is, all the nodes of depth 0 are returned, then depth 1, then 2, and so on.
   * <p/>
   * <p>No guarantees are made about the behavior of the traversal when nodes change while
   * iteration is in progress or when the iterators generated by {@code tree} are advanced.
   */
  @NotNull
  public static final TreeTraversal PLAIN_BFS = new TreeTraversal("PLAIN_BFS") {
    @NotNull
    @Override
    public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots, @NotNull Function<T, ? extends Iterable<? extends T>> tree) {
      return new PlainBfsIt<T>(roots, tree);
    }
  };

  /**
   * Same as {@code PLAIN_BFS} but with {@code TracingIt}.
   * That is, a path to the current node can be retrieved during some traversal.
   * @see TreeTraversal.TracingIt
   */
  @NotNull
  public static final TreeTraversal TRACING_BFS = new TreeTraversal("TRACING_BFS") {
    @NotNull
    @Override
    public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots, @NotNull Function<T, ? extends Iterable<? extends T>> tree) {
      return new TracingBfsIt<T>(roots, tree);
    }
  };

  /**
   * Returns an iterator over the leaf nodes only in a tree structure, using BFS traversal.
   * That is, all the leaves of depth 0 are returned, then depth 1, then 2, and so on.
   */
  @NotNull
  public static final TreeTraversal LEAVES_BFS = new TreeTraversal("LEAVES_BFS") {
    @NotNull
    @Override
    public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots, @NotNull Function<T, ? extends Iterable<? extends T>> tree) {
      return new LeavesBfsIt<T>(roots, tree);
    }
  };

  // -----------------------------------------------------------------------------
  // Iterators: DFS
  // -----------------------------------------------------------------------------

  private abstract static class DfsIt<T, H extends P<T, H>> extends TracingIt<T> {

    H last;

    protected DfsIt(Function<T, ? extends Iterable<? extends T>> tree) {
      super(tree);
    }

    @Nullable
    public T parent() {
      if (last == null) throw new NoSuchElementException();

      H p = last.parent;
      return p == null ? null : p.node == null ? null : _transform(p.node);
    }

    @NotNull
    public JBIterable<T> backtrace() {
      if (last == null) throw new NoSuchElementException();
      return _transform(JBIterable.generate(last, P.<T>toPrev()).transform(P.<T>toNode()).filter(Condition.NOT_NULL));
    }
  }

  private final static class PreOrderIt<T> extends DfsIt<T, P1<T>> {

    PreOrderIt(@NotNull Iterable<? extends T> roots, Function<T, ? extends Iterable<? extends T>> tree) {
      super(tree);
      last = P1.create(roots);
    }

    @Override
    public T nextImpl() {
      while (last != null) {
        Iterator<? extends T> it = last.iterator(tree);
        if (it.hasNext()) {
          T result = it.next();
          last = last.add(P1.create(result));
          return result;
        }
        else {
          last = last.remove();
        }
      }
      return stop();
    }
  }

  private static final class PostOrderIt<T> extends DfsIt<T, P1<T>> {

    PostOrderIt(@NotNull Iterable<? extends T> roots, Function<T, ? extends Iterable<? extends T>> tree) {
      super(tree);
      for (T root : roots) {
        P1<T> p = P1.create(root);
        last = last == null ? p : last.add(p);
      }
    }

    @Nullable
    @Override
    public T parent() {
      return last == null || last.node == null ? null : _transform(last.node);
    }

    @NotNull
    @Override
    public JBIterable<T> backtrace() {
      return last == null ? JBIterable.of(current()) : JBIterable.of(current()).append(super.backtrace());
    }

    @Override
    public T nextImpl() {
      while (last != null) {
        Iterator<? extends T> it = last.iterator(tree);
        if (it.hasNext()) {
          T result = it.next();
          last = last.add(P1.create(result));
        }
        else {
          T result = last.node;
          last = last.remove();
          return result;
        }
      }
      return stop();
    }
  }

  private final static class LeavesDfsIt<T> extends DfsIt<T, P1<T>> {

    LeavesDfsIt(@NotNull Iterable<? extends T> roots, Function<T, ? extends Iterable<? extends T>> tree) {
      super(tree);
      last = P1.create(roots);
    }

    @Override
    public T nextImpl() {
      while (last != null) {
        P1<T> top = last;
        if (top.iterator(tree).hasNext() && !top.empty) {
          T child = top.iterator(tree).next();
          last = last.add(P1.create(child));
        }
        else {
          last = last.remove();
          if (top.empty) return last == null ? stop() : top.node;
        }
      }
      return stop();
    }
  }

  private final static class InterleavedIt<T> extends DfsIt<T, P2<T>> {

    P2<T> cur, max;

    InterleavedIt(@NotNull Iterable<? extends T> roots, Function<T, ? extends Iterable<? extends T>> tree) {
      super(tree);
      last = P2.create(roots);
      cur = max = last;
    }

    @Override
    public T nextImpl() {
      while (last != null) {
        if (cur == null) {
          cur = max;
          max = max.next;
        }
        Iterator<? extends T> it = cur.iterator(tree);
        if (it.hasNext()) {
          T result = it.next();
          last = last.add(P2.create(result));
          last.parent = cur;
          cur = cur.prev;
          if (max == null) {
            max = last;
          }
          return result;
        }
        else {
          if (cur == last) {
            last = cur.prev;
          }
          cur = cur.remove();
        }
      }
      return stop();
    }
  }


  // -----------------------------------------------------------------------------
  // Iterators: BFS
  // -----------------------------------------------------------------------------

  private static final class PlainBfsIt<T> extends It<T> {

    final ArrayDeque<T> queue = new ArrayDeque<T>();
    P1<T> top;

    PlainBfsIt(@NotNull Iterable<? extends T> roots, Function<T, ? extends Iterable<? extends T>> tree) {
      super(tree);
      JBIterable.from(roots).addAllTo(queue);
    }

    @Override
    public T nextImpl() {
      if (top != null) {
        JBIterable.from(top.iterable(tree)).addAllTo(queue);
        top = null;
      }
      if (queue.isEmpty()) return stop();
      top = P1.create(queue.remove());
      return top.node;
    }
  }

  private static final class LeavesBfsIt<T> extends TracingIt<T> {

    final ArrayDeque<T> queue = new ArrayDeque<T>();

    LeavesBfsIt(@NotNull Iterable<? extends T> roots, Function<T, ? extends Iterable<? extends T>> tree) {
      super(tree);
      JBIterable.from(roots).addAllTo(queue);
    }

    @Override
    public T nextImpl() {
      while (!queue.isEmpty()) {
        T result = queue.remove();
        Iterable<? extends T> children = tree.fun(result);
        Iterator<? extends T> it = children == null ? null: children.iterator();
        if (it == null || !it.hasNext()) return result;
        while (it.hasNext()) queue.add(it.next());
      }
      return stop();
    }
  }

  private final static class TracingBfsIt<T> extends TracingIt<T> {

    final ArrayDeque<T> queue = new ArrayDeque<T>();
    final Map<T, T> paths = ContainerUtil.newTroveMap(ContainerUtil.<T>identityStrategy());
    P1<T> top;

    TracingBfsIt(@NotNull Iterable<? extends T> roots, Function<T, ? extends Iterable<? extends T>> tree) {
      super(tree);
      JBIterable.from(roots).addAllTo(queue);
    }

    @Override
    public T nextImpl() {
      if (top != null) {
        for (T t : top.iterable(tree)) {
          if (paths.containsKey(t)) continue;
          queue.add(t);
          paths.put(t, top.node);
        }
        top = null;
      }
      if (queue.isEmpty()) return stop();
      top = P1.create(queue.remove());
      return top.node;
    }

    @Override
    public T parent() {
      if (top == null) throw new NoSuchElementException();
      return _transform(paths.get(top.node));
    }

    @NotNull
    @Override
    public JBIterable<T> backtrace() {
      if (top == null) throw new NoSuchElementException();
      return _transform(JBIterable.generate(top.node, Functions.fromMap(paths)));
    }
  }

  // -----------------------------------------------------------------------------
  // Misc
  // -----------------------------------------------------------------------------
  private static final class GuidedItImpl<T> extends GuidedIt<T> {
    final Guide<T> guide;

    P1<T> first, last;
    T curResult;

    GuidedItImpl(@NotNull Iterable<? extends T> roots,
                 @NotNull Function<T, ? extends Iterable<? extends T>> tree,
                 @NotNull Guide<T> guide) {
      super(tree);
      first = last = P1.create(roots);
      this.guide = guide;
    }

    public GuidedIt<T> queueNext(T child) {
      if (child != null) last = last.add(P1.create(child));
      return this;
    }

    public GuidedIt<T> queueLast(T child) {
      if (child != null) first = first.addBefore(P1.create(child));
      return this;
    }

    public GuidedIt<T> result(T node) {
      curResult = node;
      return this;
    }

    @Override
    public T nextImpl() {
      if (guide == null) return stop();
      while (last != null) {
        P<T, ?> top = last;
        Iterator<? extends T> it = top.iterator(tree);
        boolean hasNext = it.hasNext();
        curResult = null;
        if (top.node != null || hasNext) {
          curChild = hasNext ? it.next() : null;
          curParent = top.node;
          curChildren = top.itle;
          curNoChildren = top.empty;
          guide.guide(this);
        }
        if (!hasNext) {
          last = last.remove();
        }
        if (curResult != null) {
          return curResult;
        }
      }
      return stop();
    }
  }

  private static class P<T, Self extends P<T, Self>> {
    T node;
    Iterable<? extends T> itle;
    Iterator<? extends T> it;
    boolean empty;

    Self parent;

    static <T, Self extends P<T, Self>> Self create(Self p, T node) {
      p.node = node;
      return p;
    }

    static <T, Self extends P<T, Self>> Self create(Self p, Iterable<? extends T> it) {
      p.itle = it;
      return p;
    }


    final Iterator<? extends T> iterator(@NotNull Function<T, ? extends Iterable<? extends T>> tree) {
      if (it != null) return it;
      it = iterable(tree).iterator();
      empty = itle == null || !it.hasNext();
      return it;
    }

    final Iterable<? extends T> iterable(@NotNull Function<T, ? extends Iterable<? extends T>> tree) {
      return itle != null ? itle : JBIterable.from(itle = tree.fun(node));
    }

    /** @noinspection unchecked */
    static <T> Function<P<T, ?>, T> toNode() { return TO_NODE; }
    /** @noinspection unchecked */
    static <T> Function<P<T, ?>, P<T, ?>> toPrev() { return TO_PREV; }

    static final Function TO_NODE = new Function<P<?, ?>, Object>() {
      @Override
      public Object fun(P<?, ?> tp) {
        return tp.node;
      }
    };
    static final Function TO_PREV = new Function.Mono<P<?, ?>>() {
      @Override
      public P<?, ?> fun(P<?, ?> tp) {
        return tp.parent;
      }
    };
  }

  private static final class P1<T> extends P<T, P1<T>> {

    static <T> P1<T> create(T node) { return create(new P1<T>(), node); }
    static <T> P1<T> create(Iterable<? extends T> it) { return create(new P1<T>(), it); }

    P1<T> add(@NotNull P1<T> next) {
      next.parent = this;
      return next;
    }

    P1<T> addBefore(@NotNull P1<T> next) {
      next.parent = null;
      this.parent = next;
      return next;
    }

    P1<T> remove() {
      P1<T> p = parent;
      parent = null;
      return p;
    }

    @Override
    public String toString() {
      int h = 0;
      for (P1<T> p = parent; p != null; p = p.parent) h++;
      return h + ": " + node;
    }
  }

  private static final class P2<T> extends P<T, P2<T>> {
    P2<T> next, prev;

    static <T> P2<T> create(T node) { return create(new P2<T>(), node); }
    static <T> P2<T> create(Iterable<? extends T> it) { return create(new P2<T>(), it); }

    P2<T> add(@NotNull P2<T> next) {
      next.next = this.next;
      next.prev = this;
      this.next = next;
      return next;
    }

    P2<T> remove() {
      P2<T> p = prev;
      P2<T> n = next;
      prev = next = null;
      if (p != null) p.next = n;
      if (n != null) n.prev = p;
      return p;
    }

    @Override
    public String toString() {
      int h = 0, t = 0;
      for (P2<T> p = prev; p != null; p = p.prev) h++;
      for (P2<T> p = next; p != null; p = p.next) t++;
      return h + " of " + (h + t + 1) + ": " + node;
    }
  }
}
