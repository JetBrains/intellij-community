// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.Functions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Iterator that accumulates transformations and filters keeping its instance.
 * So JBIterable#filter() and JBIterable#transform() preserve the underlying iterator API.
 *
 * <h3>Supported contracts:</h3>
 * <ul>
 *   <li>Classic iterator: hasNext() / next()</li>
 *   <li>Cursor: advance() / current()</li>
 *   <li>One-time iterable: cursor()</li>
 * </ul>
 *
 * Implementors should provide nextImpl() method which can call stop()/skip().
 *
 * @see JBIterable#map(Function)
 * @see JBIterable#filter(Condition)
 * @see TreeTraversal.TracingIt
 *
 * @author gregsh
 *
 * @noinspection unchecked, TypeParameterHidesVisibleType, AssignmentToForLoopParameter
 */
public abstract class JBIterator<E> implements Iterator<E> {

  public static @NotNull <E extends JBIterator<?>> JBIterable<E> cursor(@NotNull E iterator) {
    return JBIterable.generate(iterator, Functions.id()).intercept(CURSOR_NEXT);
  }

  public static @NotNull <E> JBIterator<E> from(final @NotNull Iterator<? extends E> it) {
    return it instanceof JBIterator ? (JBIterator<E>)it : wrap(it);
  }

  static @NotNull <E> JBIterator<E> wrap(final @NotNull Iterator<? extends E> it) {
    return new JBIterator<E>() {
      @Override
      protected E nextImpl() {
        return it.hasNext() ? it.next() : stop();
      }
    };
  }

  private enum Do {INIT, STOP, SKIP}
  private Object myCurrent = Do.INIT;
  private Object myNext = Do.INIT;

  private Op myFirstOp = new NextOp();
  private Op myLastOp = myFirstOp;

  /**
   * Returns the next element if any; otherwise calls stop() or skip().
   */
  protected abstract E nextImpl();

  /**
   * Called right after the new current value is set.
   */
  protected void currentChanged() { }

  /**
   * Notifies the iterator that there's no more elements.
   */
  protected final @Nullable E stop() {
    myNext = Do.STOP;
    return null;
  }

  /**
   * Notifies the iterator to skip and re-invoke nextImpl().
   */
  protected final @Nullable E skip() {
    myNext = Do.SKIP;
    return null;
  }

  @Override
  public final boolean hasNext() {
    peekNext();
    return myNext != Do.STOP;
  }

  @Override
  public final E next() {
    advance();
    return current();
  }

  /**
   * Proceeds to the next element if any and returns true; otherwise false.
   */
  public final boolean advance() {
    myCurrent = Do.INIT;
    peekNext();
    if (myNext == Do.STOP) return false;
    myCurrent = myNext;
    myNext = Do.INIT;
    if (myFirstOp instanceof JBIterator.CursorOp) {
      ((CursorOp)myFirstOp).advance(myCurrent);
    }
    currentChanged();
    return true;
  }

  /**
   * Returns the current element if any; otherwise throws exception.
   */
  public final E current() {
    if (myCurrent == Do.INIT) {
      throw new NoSuchElementException();
    }
    return (E)myCurrent;
  }

  private void peekNext() {
    if (myNext != Do.INIT) return;
    Object o = Do.INIT;
    for (Op op = myFirstOp; op != null; op = op == null ? myFirstOp : op.nextOp) {
      o = op.apply(op.impl == null ? nextImpl() : o);
      if (myNext == Do.STOP) return;
      if (myNext == Do.SKIP) {
        o = myNext = Do.INIT;
        if (op.impl == null) {
          // rollback all prepended takeWhile conditions if nextImpl() votes SKIP
          for (Op op2 = myFirstOp; op2.impl instanceof CountDown; op2 = op2.nextOp) {
            ((CountDown<?>)op2.impl).cur ++;
          }
        }
        op = null;
      }
    }
    myNext = o;
  }

  public final @NotNull <T> JBIterator<T> map(@NotNull Function<? super E, ? extends T> function) {
    return addOp(true, new MapOp<E, T>(function));
  }

  public final @NotNull JBIterator<E> filter(@NotNull Condition<? super E> condition) {
    return addOp(true, new FilterOp<E>(condition));
  }

  public final @NotNull <T> JBIterator<T> filterMap(@NotNull Function<? super E, ? extends T> function) {
    return addOp(true, new FilterMapOp<E, T>(function));
  }

  public final @NotNull JBIterator<E> take(int count) {
    // add first so that the underlying iterator stay on 'count' position
    return addOp(!(myLastOp instanceof NextOp), new WhileOp<>(new CountDown<>(count)));
  }

  public final @NotNull JBIterator<E> takeWhile(@NotNull Condition<? super E> condition) {
    return addOp(true, new WhileOp<E>(condition));
  }

  public final @NotNull JBIterator<E> skip(int count) {
    return skipWhile(new CountDown<>(count));
  }

  public final @NotNull JBIterator<E> skipWhile(final @NotNull Condition<? super E> condition) {
    return addOp(true, new SkipOp<E>(condition));
  }

  private @NotNull <T> T addOp(boolean last, @NotNull Op op) {
    if (op.impl == null) {
      myFirstOp = myLastOp = op;
    }
    else if (last) {
      myLastOp.nextOp = op;
      myLastOp = myLastOp.nextOp;
    }
    else {
      op.nextOp = myFirstOp;
      myFirstOp = op;
    }
    return (T)this;
  }

  @Override
  public final void remove() {
    throw new UnsupportedOperationException();
  }

  public final @NotNull @Unmodifiable List<E> toList() {
    return Collections.unmodifiableList(ContainerUtil.newArrayList(JBIterable.once(this)));
  }

  @Override
  public String toString() {
    List<Op> ops = operationsImpl().toList();
    return "{cur=" + myCurrent + "; next=" + myNext + (ops.size() < 2 ? "" : "; ops=" + ops) + "}";
  }

  public final @NotNull JBIterable<Function<Object, Object>> getTransformations() {
    return (JBIterable<Function<Object, Object>>)(JBIterable)operationsImpl().map(op -> op.impl).filter(Function.class);
  }

  private @NotNull JBIterable<Op> operationsImpl() {
    return JBIterable.generate(myFirstOp, op -> op.nextOp);
  }

  static @NotNull String toShortString(@NotNull Object o) {
    String name = o.getClass().getName();
    int idx = name.lastIndexOf('$');
    if (idx > 0 && idx + 1 < name.length() && StringUtil.isJavaIdentifierStart(name.charAt(idx + 1))) {
      return name.substring(idx + 1);
    }
    return name.substring(name.lastIndexOf('.') + 1);
  }

  private static final Function.Mono CURSOR_NEXT = new Function.Mono<JBIterator<?>>() {
    @Override
    public JBIterator<?> fun(JBIterator<?> iterator) {
      return iterator.addOp(false, iterator.new CursorOp());
    }
  };

  private static class Op<T> {
    final T impl;
    Op nextOp;

    Op(T impl) {
      this.impl = impl;
    }

    Object apply(Object o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return toShortString(impl == null ? this : impl);
    }
  }

  private static final class CountDown<A> implements Condition<A> {
    int cur;

    CountDown(int count) {
      cur = count;
    }

    @Override
    public boolean value(A a) {
      if (cur <= 0) return false;
      cur--;
      return true;
    }
  }

  private static final class MapOp<E, T> extends Op<Function<? super E, ? extends T>> {
    MapOp(Function<? super E, ? extends T> function) {
      super(function);
    }

    @Override
    Object apply(Object o) {
      return impl.fun((E)o);
    }
  }

  private final class FilterOp<E> extends Op<Condition<? super E>> {
    FilterOp(Condition<? super E> condition) {
      super(condition);
    }

    @Override
    Object apply(Object o) {
      return impl.value((E)o) ? o : skip();
    }
  }

  private final class FilterMapOp<E, T> extends Op<Function<? super E, ? extends T>> {
    FilterMapOp(Function<? super E, ? extends T> function) {
      super(function);
    }

    @Override
    Object apply(Object o) {
      Object e = impl.fun((E)o);
      return e != null ? e : skip();
    }
  }

  private final class WhileOp<E> extends Op<Condition<? super E>> {

    WhileOp(Condition<? super E> condition) {
      super(condition);
    }
    @Override
    Object apply(Object o) {
      return impl.value((E)o) ? o : stop();
    }
  }

  private final class SkipOp<E> extends Op<Condition<? super E>> {
    boolean active = true;

    SkipOp(Condition<? super E> condition) {
      super(condition);
    }

    @Override
    Object apply(Object o) {
      if (active && impl.value((E)o)) return skip();
      active = false;
      return o;
    }
  }

  private static final class NextOp extends Op<Void> {
    NextOp() {
      super(null);
    }

    @Override
    Object apply(Object o) {
      return o;
    }
  }

  private final class CursorOp extends Op<Void> {
    boolean advanced;

    CursorOp() {
      super(null);
    }

    @Override
    Object apply(Object o) {
      JBIterator<?> it = (JBIterator<?>)o;
      advanced = nextOp != null;
      return (advanced ? it.advance() : it.hasNext()) ? it : stop();
    }

    void advance(Object o) {
      if (advanced || !(o instanceof JBIterator)) return;
      ((JBIterator<?>)o).advance();
      advanced = true;
    }
  }
}
