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
import com.intellij.openapi.util.Conditions;
import com.intellij.util.Function;
import com.intellij.util.Functions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.util.Conditions.not;

public abstract class FilteredTraverserBase<T, Self extends FilteredTraverserBase<T, Self>> implements Iterable<T> {

  final Meta<T> myMeta;

  protected FilteredTraverserBase(@NotNull Meta<T> meta) {
    myMeta = meta;
  }

  @NotNull
  public Function<? super T, ? extends Iterable<? extends T>> getTree() {
    return myMeta.tree;
  }

  /**
   * Not supported if {@link #mapImpl(Function)} is applied.
   */
  @NotNull
  public final T getRoot() {
    return myMeta.roots.iterator().next();
  }

  /**
   * Not supported if {@link #mapImpl(Function)} is applied.
   */
  @NotNull
  public final Iterable<? extends T> getRoots() {
    return myMeta.roots;
  }

  @Override
  public final Iterator<T> iterator() {
    return traverse().iterator();
  }

  @NotNull
  protected abstract Self newInstance(@NotNull Meta<T> meta);

  @NotNull
  public final JBIterable<T> traverse(@NotNull TreeTraversal traversal) {
    Function<T, Iterable<? extends T>> adjusted = this::children;
    return myMeta.interceptor.fun(traversal).traversal(getRoots(), adjusted).filter(myMeta.filter.and());
  }

  @NotNull
  public final JBIterable<T> traverse() {
    return traverse(myMeta.traversal);
  }

  @NotNull
  public final JBIterable<T> biOrderDfsTraversal() {
    return traverse(TreeTraversal.BI_ORDER_DFS);
  }

  @NotNull
  public final JBIterable<T> preOrderDfsTraversal() {
    return traverse(TreeTraversal.PRE_ORDER_DFS);
  }

  @NotNull
  public final JBIterable<T> postOrderDfsTraversal() {
    return traverse(TreeTraversal.POST_ORDER_DFS);
  }

  @NotNull
  public final JBIterable<T> bfsTraversal() {
    return traverse(TreeTraversal.PLAIN_BFS);
  }

  @NotNull
  public final JBIterable<T> tracingBfsTraversal() {
    return traverse(TreeTraversal.TRACING_BFS);
  }

  /**
   * Clears expand, regard and filter conditions, traversal while keeping roots and "forced" properties.
   * @see FilteredTraverserBase#forceIgnore(Condition)
   * @see FilteredTraverserBase#forceDisregard(Condition)
   */
  @NotNull
  public final Self reset() {
    return newInstance(myMeta.reset());
  }

  @NotNull
  public final Self withRoot(@Nullable T root) {
    return newInstance(myMeta.withRoots(JBIterable.of(root)));
  }

  @NotNull
  public final Self withRoots(T @Nullable ... roots) {
    return newInstance(myMeta.withRoots(JBIterable.of(roots)));
  }

  @NotNull
  public final Self withRoots(@Nullable Iterable<? extends T> roots) {
    return newInstance(myMeta.withRoots(roots != null ? roots : JBIterable.empty()));
  }

  @NotNull
  public final Self withTraversal(TreeTraversal type) {
    return newInstance(myMeta.withTraversal(type));
  }

  /**
   * Restricts the nodes that can have children by the specified condition.
   * Subsequent calls will AND all the conditions.
   */
  @NotNull
  public final Self expand(@NotNull Condition<? super T> c) {
    return newInstance(myMeta.expand(c));
  }

  /**
   * Restricts the nodes that can be children by the specified condition while <b>keeping the edges</b>.
   * Subsequent calls will AND all the conditions.
   */
  @NotNull
  public final Self regard(@NotNull Condition<? super T> c) {
    return newInstance(myMeta.regard(c));
  }

  @NotNull
  public final Self expandAndFilter(Condition<? super T> c) {
    return newInstance(myMeta.expand(c).filter(c));
  }

  @NotNull
  public final Self expandAndSkip(Condition<? super T> c) {
    return newInstance(myMeta.expand(c).filter(not(c)));
  }

  @NotNull
  public final Self filter(@NotNull Condition<? super T> c) {
    return newInstance(myMeta.filter(c));
  }

  @NotNull
  public final <C> JBIterable<C> filter(@NotNull Class<C> type) {
    return traverse().filter(type);
  }

  /**
   * Configures the traverser to skip already visited nodes.
   * <p/>
   * This property is not reset by {@code reset()} call.
   * @see FilteredTraverserBase#unique(Function)
   * @see TreeTraversal#unique()
   */
  @NotNull
  public final Self unique() {
    return unique(Functions.identity());
  }

  /**
   * Configures the traverser to skip already visited nodes.
   * <p/>
   * This property is not reset by {@code reset()} call.
   * @see TreeTraversal#unique(Function)
   */
  @NotNull
  public final Self unique(@NotNull final Function<? super T, Object> identity) {
    return intercept(traversal -> traversal.unique(identity));
  }

  /**
   * Configures the traverser to cache its structure.
   * <p/>
   * This property is not reset by {@code reset()} call.
   * @see TreeTraversal#cached(Map)
   */
  @NotNull
  public final Self cached() {
    IdentityHashMap<Object, Object> cache = new IdentityHashMap<>();
    return intercept(traversal -> traversal.cached(cache));
  }

  /**
   * Configures the traverser to expand and return the nodes within the range only.
   * <p/>
   * This property is not reset by {@code reset()} call.
   * @see TreeTraversal#onRange(Condition)
   */
  @NotNull
  public Self onRange(@NotNull final Condition<? super T> rangeCondition) {
    return intercept(traversal -> traversal.onRange(rangeCondition));
  }

  /**
   * Excludes the nodes by the specified condition from any traversal completely.
   * <p/>
   * This property is not reset by {@code reset()} call.
   * @see FilteredTraverserBase#expand(Condition)
   * @see FilteredTraverserBase#filter(Condition)
   * @see FilteredTraverserBase#reset()
   */
  @NotNull
  public final Self forceIgnore(@NotNull Condition<? super T> c) {
    return newInstance(myMeta.forceIgnore(c));
  }

  /**
   * Excludes the nodes by the specified condition while keeping their edges.
   * <p/>
   * This property is not reset by {@code reset()} call.
   * @see FilteredTraverserBase#regard(Condition)
   * @see FilteredTraverserBase#reset()
   */
  @NotNull
  public final Self forceDisregard(@NotNull Condition<? super T> c) {
    return newInstance(myMeta.forceDisregard(c));
  }

  /**
   * Intercepts and alters traversal just before the walking.
   * <p/>
   * This property is not reset by {@code reset()} call.
   * @see FilteredTraverserBase#unique()
   * @see FilteredTraverserBase#onRange(Condition)
   */
  @NotNull
  public final Self intercept(@NotNull Function<? super TreeTraversal, ? extends TreeTraversal> transform) {
    return newInstance(myMeta.interceptTraversal(transform));
  }

  /**
   * Returns a tree traverser that applies {@code function} to each element of this traverser.
   * A reverse transform is required if available, otherwise use {@link #mapImpl(Function)}.
   *
   * Subclasses shall provide their own public method to get a more strict return type.
   * @see JBTreeTraverser#map(Function)
   */
  @NotNull
  protected <S, SelfS extends FilteredTraverserBase<S, ?>> SelfS mapImpl(@NotNull Function<? super T, ? extends S> function,
                                                                         @NotNull Function<? super S, ? extends T> reverse) {
    //TODO support stateful functions as mapImpl(Function)
    Function<T, JBIterable<T>> baseTree = myMeta::children;
    Condition<? super T> filter = myMeta.filter.and();
    Meta<S> meta = Meta.<S>create(s -> baseTree.fun(reverse.fun(s)).map(function))
      .withRoots(JBIterable.from(getRoots()).map(function))
      .filter(filter == Conditions.alwaysTrue() ? Conditions.alwaysTrue() : o -> filter.value(reverse.fun(o)));
    //noinspection unchecked
    return (SelfS)newInstance((Meta<T>)meta);
  }

  /**
   * Returns a tree traverser that applies {@code function} to each element of this traverser.
   * The required reverse transform, a hash map, is built internally while traversing.
   * Prefer {@link #mapImpl(Function, Function)} if a cheap reverse transform is available.
   *
   * Subclasses shall provide their own public method to get a more strict return type.
   * @see JBTreeTraverser#map(Function, Function)
   */
  @NotNull
  protected <S, SelfS extends FilteredTraverserBase<S, ?>> SelfS mapImpl(@NotNull Function<? super T, ? extends S> function) {
    Meta<S> meta = new Meta<>(
      JBIterable.empty(), myMeta.traversal, Functions.constant(JBIterable.empty()),
      Cond.TRUE, Cond.TRUE, Cond.TRUE, Cond.FALSE, Cond.FALSE, Cond.FALSE,
      original -> new MappedTraversal<T, S>(original, myMeta, JBIterable.Stateful.copy(function)), myMeta);
    //noinspection unchecked
    return (SelfS)newInstance((Meta<T>)meta);
  }

  /**
   * Returns raw children of the specified node as seen by this traverser.
   *
   * Shall be avoided as it circumvents normal traversal procedure.
   * Not supported if {@link #mapImpl(Function)} is applied.
   *
   * @see TreeTraversal.TracingIt to obtain parents of a node during a traversal
   */
  @ApiStatus.Internal
  @NotNull
  public final JBIterable<T> children(@Nullable T node) {
    return myMeta.children(node);
  }

  @NotNull
  public final List<T> toList() {
    return traverse().toList();
  }

  @NotNull
  public final Set<T> toSet() {
    return traverse().toSet();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" +
           "traversal=" + myMeta.traversal +
           '}';
  }

  public abstract static class EdgeFilter<T> extends JBIterable.SCond<T> {

    protected T edgeSource;

  }

  protected static class Meta<T> {
    final TreeTraversal traversal;

    final Iterable<? extends T> roots;
    final Function<? super T, ? extends Iterable<? extends T>> tree;
    final Cond<T> expand;
    final Cond<T> regard;
    final Cond<T> filter;

    final Cond<T> forceExpand;
    final Cond<T> forceIgnore;
    final Cond<T> forceDisregard;

    final Function<? super TreeTraversal, ? extends TreeTraversal> interceptor;
    final Meta<?> original;

    @NotNull
    public static <T> Meta<T> create(Function<? super T, ? extends Iterable<? extends T>> tree) {
      return new Meta<>(JBIterable.empty(), TreeTraversal.PRE_ORDER_DFS,
                        tree, Cond.TRUE, Cond.TRUE, Cond.TRUE, Cond.FALSE, Cond.FALSE, Cond.FALSE,
                        Functions.id(), null);
    }


    /** @noinspection unchecked*/
    Meta(@NotNull Iterable<? extends T> roots,
         @NotNull TreeTraversal traversal,
         @NotNull Function<? super T, ? extends Iterable<? extends T>> tree,
         @NotNull Cond<? super T> expand,
         @NotNull Cond<? super T> regard,
         @NotNull Cond<? super T> filter,
         @NotNull Cond<? super T> forceExpand,
         @NotNull Cond<? super T> forceIgnore,
         @NotNull Cond<? super T> forceDisregard,
         @NotNull Function<? super TreeTraversal, ? extends TreeTraversal> interceptor,
         @Nullable Meta<?> original) {
      this.roots = roots;
      this.traversal = traversal;
      this.tree = tree;
      this.expand = (Cond<T>)expand;
      this.regard = (Cond<T>)regard;
      this.filter = (Cond<T>)filter;
      this.forceExpand = (Cond<T>)forceExpand;
      this.forceIgnore = (Cond<T>)forceIgnore;
      this.forceDisregard = (Cond<T>)forceDisregard;
      this.interceptor = interceptor;
      this.original = original;
    }

    public Meta<T> reset() {
      return new Meta<>(roots, TreeTraversal.PRE_ORDER_DFS, tree, Cond.TRUE, Cond.TRUE, Cond.TRUE,
                        forceExpand, forceIgnore, forceDisregard, interceptor, original);
    }

    public Meta<T> withRoots(@NotNull Iterable<? extends T> roots) {
      return new Meta<>(roots, traversal, tree, expand, regard, filter, forceExpand, forceIgnore, forceDisregard, interceptor, original);
    }

    public Meta<T> withTraversal(TreeTraversal traversal) {
      return new Meta<>(roots, traversal, tree, expand, regard, filter, forceExpand, forceIgnore, forceDisregard, interceptor, original);
    }

    public Meta<T> expand(@NotNull Condition<? super T> c) {
      return new Meta<>(roots, traversal, tree, expand.append(c), regard, filter, forceExpand, forceIgnore, forceDisregard, interceptor, original);
    }

    public Meta<T> regard(@NotNull Condition<? super T> c) {
      return new Meta<>(roots, traversal, tree, expand, regard.append(c), filter, forceExpand, forceIgnore, forceDisregard, interceptor, original);
    }

    public Meta<T> filter(@NotNull Condition<? super T> c) {
      return new Meta<>(roots, traversal, tree, expand, regard, filter.append(c), forceExpand, forceIgnore, forceDisregard, interceptor, original);
    }

    public Meta<T> forceExpand(Condition<? super T> c) {
      return new Meta<>(roots, traversal, tree, expand, regard, filter, forceExpand.append(c), forceIgnore, forceDisregard, interceptor, original);
    }

    public Meta<T> forceIgnore(Condition<? super T> c) {
      return new Meta<>(roots, traversal, tree, expand, regard, filter, forceExpand, forceIgnore.append(c), forceDisregard, interceptor, original);
    }

    public Meta<T> forceDisregard(Condition<? super T> c) {
      return new Meta<>(roots, traversal, tree, expand, regard, filter, forceExpand, forceIgnore, forceDisregard.append(c), interceptor, original);
    }

    public Meta<T> interceptTraversal(Function<? super TreeTraversal, ? extends TreeTraversal> interceptor) {
      if (interceptor == Functions.<TreeTraversal, TreeTraversal>identity()) return this;
      return new Meta<>(roots, traversal, tree, expand, regard, filter, forceExpand, forceIgnore, forceDisregard,
                        Functions.compose(this.interceptor, interceptor), original);
    }

    JBIterable<T> children(@Nullable T node) {
      return childrenImpl(node, this.tree);
    }

    JBIterable<T> childrenImpl(@Nullable T node,
                               @NotNull Function<? super T, ? extends Iterable<? extends T>> tree) {
      if (node == null) {
        return JBIterable.empty();
      }
      else if (expand != Cond.TRUE && !expand.valueAnd(node) &&
               (forceExpand == Cond.FALSE || !forceExpand.valueOr(node))) {
        return JBIterable.empty();
      }
      else {
        if (regard == Cond.TRUE && forceDisregard == Cond.FALSE) {
          return JBIterable.<T>from(tree.fun(node)).filter(not(forceIgnore.or()));
        }
        else {
          // traverse subtree to select accepted children
          return TreeTraversal.GUIDED_TRAVERSAL(createChildrenGuide(node)).traversal(node, tree);
        }
      }
    }

    TreeTraversal.GuidedIt.Guide<T> createChildrenGuide(final T parent) {
      return new TreeTraversal.GuidedIt.Guide<T>() {
        final Condition<? super T> expand = buildExpandConditionForChildren(parent);
        @Override
        public void guide(@NotNull TreeTraversal.GuidedIt<T> guidedIt) {
          doPerformChildrenGuidance(guidedIt, expand);
        }
      };
    }

    private void doPerformChildrenGuidance(TreeTraversal.GuidedIt<T> it, Condition<? super T> expand) {
      if (it.curChild == null) return;
      if (forceIgnore != Cond.FALSE && forceIgnore.valueOr(it.curChild)) return;
      if (it.curParent == null || expand == Conditions.alwaysTrue() || expand.value(it.curChild)) {
        it.queueNext(it.curChild);
      }
      else {
        it.result(it.curChild);
      }
    }

    private Condition<? super T> buildExpandConditionForChildren(T parent) {
      // implements: or(forceDisregard, not(regard));
      // and handles JBIterable.StatefulTransform and EdgeFilter conditions
      //noinspection unchecked
      Condition<? super T>[] impls = new Condition[regard.length + forceDisregard.length];
      int count = 0;
      boolean isRegard = false;
      Cond<T> c = forceDisregard;
      while (c != null) {
        if (c.impl != (isRegard ? Conditions.alwaysTrue() : Conditions.alwaysFalse())) {
          Condition<? super T> impl = JBIterable.Stateful.copy(c.impl);
          if (impl instanceof EdgeFilter) {
            ((EdgeFilter)impl).edgeSource = parent;
          }
          impls[count++] = isRegard ? not(impl) : impl;
        }
        if (c.next == null) {
          c = isRegard ? null : regard;
          isRegard = true;
        }
        else {
          c = c.next;
        }
      }
      if (count <= 0) return Conditions.alwaysFalse();
      Cond<T> result = null;
      for (int i = 0; i < count; i++) {
        result = new Cond<>(impls[count - i - 1], result);
      }
      return result.or();
    }

  }

  static class Cond<T> {
    static final Cond<Object> TRUE = new Cond<>(Conditions.alwaysTrue(), null);
    static final Cond<Object> FALSE = new Cond<>(Conditions.alwaysFalse(), null);

    final Condition<? super T> impl;
    final Cond<T> next;
    final int length;

    Cond(Condition<? super T> impl, Cond<T> next) {
      this.impl = impl;
      this.next = next;
      this.length = next == null ? 1 : next.length + 1;
    }

    Cond<T> append(Condition<? super T> impl) {
      Cond<T> result = new Cond<>(impl, null);
      for (Cond<T> o : toArray(true)) {
        result = new Cond<>(o.impl, result);
      }
      return result;
    }

    boolean valueAnd(T t) {
      for (Cond<T> c = this; c != null; c = c.next) {
        if (!c.impl.value(t)) return false;
      }
      return true;
    }

    boolean valueOr(T t) {
      for (Cond<T> c = this; c != null; c = c.next) {
        if (c.impl.value(t)) return true;
      }
      return false;
    }

    Condition<? super T> or() {
      Boolean result = false;
      for (Cond<T> c = this; c != null; c = c.next) {
        if (c.impl == Conditions.alwaysTrue()) return Conditions.alwaysTrue();
        result = result != null && c.impl != Conditions.alwaysFalse() ? null : result;
      }
      return result == null ? (Condition<T>)this::valueOr : Conditions.alwaysFalse();
    }

    Condition<? super T> and() {
      Boolean result = true;
      for (Cond<T> c = this; c != null; c = c.next) {
        if (c.impl == Conditions.alwaysFalse()) return Conditions.alwaysFalse();
        result = result != null && c.impl != Conditions.alwaysTrue() ? null : result;
      }
      return result == null ? (Condition<T>)this::valueAnd : Conditions.alwaysTrue();
    }

    /** @noinspection SameParameterValue*/
    Cond<T>[] toArray(boolean inverse) {
      //noinspection unchecked
      Cond<T>[] result = new Cond[length];
      Cond<T> cur = this;
      for (int i = 0; i < length; i++, cur = cur.next) result[inverse ? length - i - 1 : i] = cur;
      return result;
    }

    @Override
    public String toString() {
      if (this == TRUE) return "Cond.TRUE";
      if (this == FALSE) return "Cond.FALSE";
      StringBuilder sb = new StringBuilder("Cond{");
      for (Cond<T> c = this; c != null; c = c.next) {
        sb.append(JBIterator.toShortString(c.impl));
        if (c.next != null) sb.append(", ");
      }
      return sb.append("}").toString();
    }
  }

  private static class MappedTraversal<T, S> extends TreeTraversal {
    final TreeTraversal original;
    final Meta<T> meta;
    final Function<? super T, ? extends S> map;

    MappedTraversal(@NotNull TreeTraversal original,
                    @NotNull Meta<T> meta,
                    @NotNull Function<? super T, ? extends S> map) {
      super(original + " (MAPPED by " + map + ")");
      this.original = original;
      this.meta = meta;
      this.map = map;
    }

    /** @noinspection unchecked */
    @NotNull
    @Override
    public <SS> It<SS> createIterator(@NotNull Iterable<? extends SS> ignore1,
                                      @NotNull Function<? super SS, ? extends Iterable<? extends SS>> ignore2) {
      List<Meta> metas = ContainerUtil.reverse(JBIterable.generate(this.meta, o -> (Meta)o.original).toList());
      Meta firstMeta = metas.get(0);

      Iterable roots = firstMeta.roots;
      Function tree = firstMeta::children;
      Condition filter = firstMeta.filter.and();

      for (int i = 1, count = metas.size(); i <= count; i++) {
        Meta meta = i < count ? metas.get(i) : null;
        TreeTraversal adjusted = meta == null ? this : (TreeTraversal)meta.interceptor.fun(original);

        tree = new MappedTree(tree, ((MappedTraversal)adjusted).map, meta);
        // Must be a separate variable, otherwise javac 8u201 crashes when compiling this code
        Function fn = ((MappedTree)tree)::map;
        roots = JBIterable.from(roots).map(fn);

        Function tree0 = tree;
        Condition filter0 = filter;
        Condition<? super Object> prevFilter = filter == Conditions.alwaysTrue() ? Conditions.alwaysTrue() :
                                               o -> filter0.value(((MappedTree)tree0).reverse(o));
        filter = Conditions.and(prevFilter, meta == null ? Conditions.alwaysTrue() : meta.filter.and());
      }

      MappedTree mappedTree = (MappedTree)tree;
      return (It<SS>)original
        .createIterator(JBIterable.from(roots), mappedTree)
        .filter(filter);
    }
  }

  private static class MappedTree<T, S> implements Function<S, Iterable<? extends S>> {
    final Function<? super T, ? extends Iterable<? extends T>> tree;
    final Function<? super T, ? extends S> mapInner;
    final Meta<S> meta;
    Map<S, T> reverse;

    MappedTree(Function<? super T, ? extends Iterable<? extends T>> tree,
               Function<? super T, ? extends S> map,
               Meta<S> meta) {
      this.tree = tree;
      this.mapInner = map;
      this.meta = meta;
    }

    @Override
    public Iterable<? extends S> fun(S s) {
      return meta == null ? JBIterable.from(tree.fun(reverse(s))).map(this::map)
                          : meta.childrenImpl(s, o -> JBIterable.from(tree.fun(reverse(o))).map(this::map));
    }

    S map(T t) {
      if (reverse == null) reverse = ContainerUtil.createWeakMap();
      S s = mapInner.fun(t);
      if (s != null && t != null) {
        reverse.put(s, t);
      }
      return s;
    }

    @NotNull
    T reverse(S s) {
      T t = s==null?null:reverse.get(s);
      if (t == null) {
        throw new IllegalStateException("unable to reverse map for: " + s);
      }
      return t;
    }
  }
}
