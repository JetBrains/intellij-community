// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.util.Function;
import com.intellij.util.Functions;
import com.intellij.util.NotNullizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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

  private static final NotNullizer ourNotNullizer = new NotNullizer("TreeTraversal.NotNull");

  private final String debugName;

  protected TreeTraversal(@NotNull String debugName) {
    this.debugName = debugName;
  }

  /**
   * Creates a new iterator for this type of traversal.
   *
   * @param roots tree roots
   * @param tree  tree structure the children for parent function.
   *              May return null (useful for map representation).
   */
  @NotNull
  public abstract <T> It<T> createIterator(@NotNull Iterable<? extends T> roots,
                                           @NotNull Function<? super T, ? extends Iterable<? extends T>> tree);


  @NotNull
  public final <T> JBIterable<T> traversal(@NotNull final Iterable<? extends T> roots, @NotNull final Function<? super T, ? extends Iterable<? extends T>> tree) {
    return new JBIterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        return createIterator(roots, tree);
      }
    };
  }

  @NotNull
  public final <T> JBIterable<T> traversal(@Nullable final T root, @NotNull final Function<? super T, ? extends Iterable<? extends T>> tree) {
    return traversal(JBIterable.of(root), tree);
  }

  @NotNull
  public final <T> Function<T, JBIterable<T>> traversal(@NotNull final Function<? super T, ? extends Iterable<? extends T>> tree) {
    return t -> traversal(t, tree);
  }

  /**
   * Configures the traversal to skip already visited nodes.
   * @see TreeTraversal#unique(Function)
   */
  @NotNull
  public final TreeTraversal unique() {
    return unique(Functions.identity());
  }

  /**
   * Configures the traversal to skip already visited nodes.
   * @param identity function
   */
  @NotNull
  public final TreeTraversal unique(@NotNull final Function<?, ?> identity) {
    return intercept("UNIQUE", new TraversalInterceptor() {
      @NotNull
      @Override
      public <TT> TraversalArgs<TT> intercept(@NotNull TraversalArgs<TT> args) {
        final Function<? super TT, ? extends Iterable<? extends TT>> tree = args.tree;
        class WrappedTree implements Condition<TT>, Function<TT, Iterable<? extends TT>> {
          final Function<?, ?> inner = identity;
          java.util.HashSet<Object> visited;

          @Override
          public boolean value(TT e) {
            if (visited == null) visited = new java.util.HashSet<>();
            //noinspection unchecked
            return visited.add(((Function<TT, Object>)inner).fun(e));
          }

          @Override
          public Iterable<? extends TT> fun(TT t) {
            return JBIterable.from(tree.fun(t)).filter(this);
          }
        }
        if (tree instanceof WrappedTree && Comparing.equal(identity, ((WrappedTree)tree).inner)) {
          return args;
        }
        WrappedTree wrappedTree = new WrappedTree();
        return new TraversalArgs<>(JBIterable.from(args.roots).filter(wrappedTree), wrappedTree);
      }
    });
  }

  /**
   * Configures the traversal to cache its structure.
   */
  @NotNull
  public final TreeTraversal cached(final Map<Object, Object> cache) {
    return intercept("CACHED", new TraversalInterceptor() {
      @NotNull
      @Override
      public <TT> TraversalArgs<TT> intercept(@NotNull TraversalArgs<TT> args) {
        final Function<? super TT, ? extends Iterable<? extends TT>> tree = args.tree;
        class WrappedTree implements Function<TT, Iterable<? extends TT>> {
          @Override
          public Iterable<? extends TT> fun(TT t) {
            //noinspection unchecked
            return (Iterable<? extends TT>)cache.computeIfAbsent(t, o -> JBIterable.from(tree.fun((TT)o)).collect());
          }
        }
        if (tree instanceof WrappedTree) {
          return args;
        }
        WrappedTree wrappedTree = new WrappedTree();
        return new TraversalArgs<>(args.roots, wrappedTree);
      }
    });
  }

  /**
   * Configures the traversal to expand and return the nodes within the range only.
   * It is an optimized version of expand-and-filter operation.
   * It skips all the nodes "before" the {@code rangeCondition} return true for the first time,
   * processes as usual the nodes while the condition return true and
   * stops when the {@code rangeCondition} return false after that.
   */
  @NotNull
  public final <T> TreeTraversal onRange(@NotNull final Condition<? super T> rangeCondition) {
    return intercept("ON_RANGE", new TraversalInterceptor() {
      @NotNull
      @Override
      public <TT> TraversalArgs<TT> intercept(@NotNull TraversalArgs<TT> args) {
        final Function<? super TT, ? extends Iterable<? extends TT>> tree = args.tree;
        final Condition<? super TT> inRangeCondition = (Condition<? super TT>)rangeCondition;
        final Condition<? super TT> notInRangeCondition = (Condition<? super TT>)not(rangeCondition);
        class WrappedTree implements Function<TT, Iterable<? extends TT>> {
          final Condition<? super T> inner = rangeCondition;

          @Override
          public Iterable<? extends TT> fun(TT t) {
            return JBIterable.from(tree.fun(t))
              .skipWhile(notInRangeCondition)
              .takeWhile(inRangeCondition);
          }
        }
        if (tree instanceof WrappedTree && Comparing.equal(rangeCondition, ((WrappedTree)tree).inner)) {
          return args;
        }
        WrappedTree wrappedTree = new WrappedTree();
        return new TraversalArgs<>(JBIterable.from(args.roots).filter(inRangeCondition), wrappedTree);
      }
    });
  }

  @NotNull
  public final TreeTraversal intercept(@NotNull String debugName, @NotNull final TraversalInterceptor interceptor) {
    String nextName = debugName + "." + this.debugName;
    if (this instanceof Intercepted) {
      Intercepted intercepted = (Intercepted)this;
      return new Intercepted(nextName, intercepted.original, TraversalInterceptor.compose(intercepted.interceptor, interceptor));
    }
    return new Intercepted(nextName, this, interceptor);
  }

  public interface TraversalInterceptor {
    @NotNull
    <T> TraversalArgs<T> intercept(@NotNull TraversalArgs<T> args);

    @NotNull
    static TraversalInterceptor compose(@NotNull TraversalInterceptor interceptor1, @NotNull TraversalInterceptor interceptor2) {
      return new TraversalInterceptor() {
        @NotNull
        @Override
        public <TT> TraversalArgs<TT> intercept(@NotNull TraversalArgs<TT> args) {
          return interceptor2.intercept(interceptor1.intercept(args));
        }
      };
    }
  }

  public static final class TraversalArgs<T> {
    public final Iterable<? extends T> roots;
    public final Function<? super T, ? extends Iterable<? extends T>> tree;

    public TraversalArgs(@NotNull Iterable<? extends T> roots,
                         @NotNull Function<? super T, ? extends Iterable<? extends T>> tree) {
      this.roots = roots;
      this.tree = tree;
    }
  }

  private static class Intercepted extends TreeTraversal {
    final TreeTraversal original;
    final TraversalInterceptor interceptor;

    protected Intercepted(@NotNull String debugName,
                          @NotNull TreeTraversal original,
                          @NotNull TraversalInterceptor interceptor) {
      super(debugName);
      this.original = original;
      this.interceptor = interceptor;
    }

    @NotNull
    @Override
    public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots,
                                    @NotNull Function<? super T, ? extends Iterable<? extends T>> tree) {
      TraversalArgs<T> adjusted = interceptor.intercept(new TraversalArgs<>(roots, tree));
      return original.createIterator(adjusted.roots, adjusted.tree);
    }
  }

  @Override
  public final String toString() {
    return debugName;
  }

  public static abstract class It<T> extends JBIterator<T> {
    protected final Function<? super T, ? extends Iterable<? extends T>> tree;

    protected It(Function<? super T, ? extends Iterable<? extends T>> tree) {
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

    public boolean isDescending() { return true; }

    protected TracingIt(Function<? super T, ? extends Iterable<? extends T>> tree) {
      super(tree);
    }

    protected JBIterable<T> _transform(JBIterable<?> original) {
      JBIterable<?> result = original;
      for (Function<Object, Object> f : _transformations()) {
        result = result.map(f);
      }
      //noinspection unchecked
      return (JBIterable<T>)result;
    }

    protected T _transform(Object original) {
      Object result = original;
      for (Function<Object, ?> f : _transformations()) {
        result = f.fun(result);
      }
      //noinspection unchecked
      return (T)result;
    }

    private JBIterable<Function<Object, Object>> cachedTransform;

    @NotNull
    private JBIterable<Function<Object, Object>> _transformations() {
      return cachedTransform == null ? cachedTransform = getTransformations() : cachedTransform;
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

    protected GuidedIt(Function<? super T, ? extends Iterable<? extends T>> tree) {
      super(tree);
    }
  }

  @NotNull
  public static TreeTraversal GUIDED_TRAVERSAL(@NotNull final GuidedIt.Guide<?> guide) {
    return new TreeTraversal("GUIDED_TRAVERSAL") {
      @NotNull
      @Override
      public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots, @NotNull Function<? super T, ? extends Iterable<? extends T>> tree) {
        //noinspection unchecked
        return new GuidedItImpl<>(roots, tree, (GuidedIt.Guide<T>)guide);
      }
    };
  }

  /**
   * Returns an iterator over the nodes in a tree structure, using bi-order traversal.
   * That is, each node returned before and after it's subtrees are traversed.
   * Direction can be retrieved through TracingIt#isDescending()
   *
   * <p>No guarantees are made about the behavior of the traversal when nodes change while
   * iteration is in progress or when the iterators generated by {@code tree} are advanced.
   */
  @NotNull
  public static final TreeTraversal BI_ORDER_DFS = new TreeTraversal("BI_ORDER_DFS") {
    @NotNull
    @Override
    public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots, @NotNull Function<? super T, ? extends Iterable<? extends T>> tree) {
      return new BiOrderIt<>(roots, tree, BiOrderIt.Order.BOTH);
    }
  };

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
    public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots, @NotNull Function<? super T, ? extends Iterable<? extends T>> tree) {
      return new BiOrderIt<>(roots, tree, BiOrderIt.Order.PRE);
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
    public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots, @NotNull Function<? super T, ? extends Iterable<? extends T>> tree) {
      return new BiOrderIt<>(roots, tree, BiOrderIt.Order.POST);
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
    public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots, @NotNull Function<? super T, ? extends Iterable<? extends T>> tree) {
      return new LeavesDfsIt<>(roots, tree);
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
    public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots, @NotNull Function<? super T, ? extends Iterable<? extends T>> tree) {
      return new InterleavedIt<>(roots, tree);
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
    public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots, @NotNull Function<? super T, ? extends Iterable<? extends T>> tree) {
      return new PlainBfsIt<>(roots, tree);
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
    public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots, @NotNull Function<? super T, ? extends Iterable<? extends T>> tree) {
      return new TracingBfsIt<>(roots, tree);
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
    public <T> It<T> createIterator(@NotNull Iterable<? extends T> roots, @NotNull Function<? super T, ? extends Iterable<? extends T>> tree) {
      return new LeavesBfsIt<>(roots, tree);
    }
  };

  // -----------------------------------------------------------------------------
  // Iterators: DFS
  // -----------------------------------------------------------------------------

  private abstract static class DfsIt<T, H extends P<T, H>> extends TracingIt<T> {

    H last;
    H cur;

    protected DfsIt(Function<? super T, ? extends Iterable<? extends T>> tree) {
      super(tree);
    }

    @Override
    protected void currentChanged() {
      cur = last;
    }

    @Override
    @Nullable
    public T parent() {
      if (cur == null) throw new NoSuchElementException();

      H p = cur.parent;
      return p == null ? null : p.node == null ? null : _transform(p.node);
    }

    @Override
    @NotNull
    public JBIterable<T> backtrace() {
      if (cur == null) throw new NoSuchElementException();
      return _transform(JBIterable.generate(cur, P.toPrev()).filterMap(P.toNode()));
    }
  }

  private static final class BiOrderIt<T> extends DfsIt<T, P1<T>> {
    private enum Order { PRE, POST, BOTH }
    private final Order order;
    private boolean curDescending;
    private boolean descending = true;

    BiOrderIt(@NotNull Iterable<? extends T> roots,
              @NotNull Function<? super T, ? extends Iterable<? extends T>> tree,
              @NotNull Order order) {
      super(tree);
      this.order = order;
      last = P1.create(roots);
    }

    @Override
    protected void currentChanged() {
      super.currentChanged();
      curDescending = descending;
    }

    @Nullable
    @Override
    public T parent() {
      return curDescending || cur == null ? super.parent() : cur.node == null ? null : _transform(cur.node);
    }

    @NotNull
    @Override
    public JBIterable<T> backtrace() {
      return curDescending ? super.backtrace() : JBIterable.of(current()).append(super.backtrace());
    }

    @Override
    public boolean isDescending() {
      return curDescending;
    }

    @Override
    public T nextImpl() {
      while (last != null) {
        Iterator<? extends T> it = last.iterator(tree);
        if (it.hasNext()) {
          T result = it.next();
          last = last.add(P1.create(result));
          descending = true;
          if (order != Order.POST) return result;
        }
        else {
          T result = last.node;
          last = last.remove();
          descending = false;
          if (order != Order.PRE && last != null) return result;
        }
      }
      descending = true;
      return stop();
    }
  }

  private final static class LeavesDfsIt<T> extends DfsIt<T, P1<T>> {

    LeavesDfsIt(@NotNull Iterable<? extends T> roots, Function<? super T, ? extends Iterable<? extends T>> tree) {
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

    InterleavedIt(@NotNull Iterable<? extends T> roots, Function<? super T, ? extends Iterable<? extends T>> tree) {
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

    final ArrayDeque<T> queue = new ArrayDeque<>();
    P1<T> top;

    PlainBfsIt(@NotNull Iterable<? extends T> roots, Function<? super T, ? extends Iterable<? extends T>> tree) {
      super(tree);
      JBIterable.from(roots).map(ourNotNullizer::notNullize).addAllTo(queue);
    }

    @Override
    public T nextImpl() {
      if (top != null) {
        JBIterable.from(top.iterable(tree)).map(ourNotNullizer::notNullize).addAllTo(queue);
        top = null;
      }
      if (queue.isEmpty()) return stop();
      top = P1.create(ourNotNullizer.nullize(queue.remove()));
      return top.node;
    }
  }

  private static final class LeavesBfsIt<T> extends TracingIt<T> {

    final ArrayDeque<T> queue = new ArrayDeque<>();

    LeavesBfsIt(@NotNull Iterable<? extends T> roots, Function<? super T, ? extends Iterable<? extends T>> tree) {
      super(tree);
      JBIterable.from(roots).map(ourNotNullizer::notNullize).addAllTo(queue);
    }

    @Override
    public T nextImpl() {
      while (!queue.isEmpty()) {
        T result = ourNotNullizer.nullize(queue.remove());
        Iterable<? extends T> children = tree.fun(result);
        Iterator<? extends T> it = children == null ? null: children.iterator();
        if (it == null || !it.hasNext()) return result;
        while (it.hasNext()) queue.add(ourNotNullizer.notNullize(it.next()));
      }
      return stop();
    }
  }

  private final static class TracingBfsIt<T> extends TracingIt<T> {

    final ArrayDeque<T> queue = new ArrayDeque<>();
    final Map<T, T> paths = new IdentityHashMap<>();
    P1<T> top;
    P1<T> cur;

    TracingBfsIt(@NotNull Iterable<? extends T> roots, Function<? super T, ? extends Iterable<? extends T>> tree) {
      super(tree);
      JBIterable.from(roots).map(ourNotNullizer::notNullize).addAllTo(queue);
    }

    @Override
    protected void currentChanged() {
      cur = top;
    }

    @Override
    public T nextImpl() {
      if (top != null) {
        for (T t : top.iterable(tree)) {
          if (paths.containsKey(t)) continue;
          queue.add(ourNotNullizer.notNullize(t));
          paths.put(t, top.node);
        }
        top = null;
      }
      if (queue.isEmpty()) return stop();
      top = P1.create(ourNotNullizer.nullize(queue.remove()));
      return top.node;
    }

    @Override
    public T parent() {
      if (cur == null) throw new NoSuchElementException();
      return _transform(paths.get(cur.node));
    }

    @NotNull
    @Override
    public JBIterable<T> backtrace() {
      if (cur == null) throw new NoSuchElementException();
      return _transform(JBIterable.generate(cur.node, Functions.fromMap(paths)));
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
                 @NotNull Function<? super T, ? extends Iterable<? extends T>> tree,
                 @NotNull Guide<T> guide) {
      super(tree);
      first = last = P1.create(roots);
      this.guide = guide;
    }

    @Override
    public GuidedIt<T> queueNext(T child) {
      if (child != null) last = last.add(P1.create(child));
      return this;
    }

    @Override
    public GuidedIt<T> queueLast(T child) {
      if (child != null) first = first.addBefore(P1.create(child));
      return this;
    }

    @Override
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


    final Iterator<? extends T> iterator(@NotNull Function<? super T, ? extends Iterable<? extends T>> tree) {
      if (it != null) return it;
      it = iterable(tree).iterator();
      empty = itle == null || !it.hasNext();
      return it;
    }

    final Iterable<? extends T> iterable(@NotNull Function<? super T, ? extends Iterable<? extends T>> tree) {
      return itle != null ? itle : (itle = tree.fun(node)) != null ? itle : JBIterable.empty();
    }

    /** @noinspection unchecked */
    static <T> Function<P<T, ?>, T> toNode() { return TO_NODE; }
    /** @noinspection unchecked */
    static <T> Function<P<T, ?>, P<T, ?>> toPrev() { return TO_PREV; }

    static final Function TO_NODE = (Function<P<?, ?>, Object>)tp -> tp.node;
    static final Function TO_PREV = new Function.Mono<P<?, ?>>() {
      @Override
      public P<?, ?> fun(P<?, ?> tp) {
        return tp.parent;
      }
    };
  }

  private static final class P1<T> extends P<T, P1<T>> {

    static <T> P1<T> create(T node) { return create(new P1<>(), node); }
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
      return parent;
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

    static <T> P2<T> create(T node) { return create(new P2<>(), node); }
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
