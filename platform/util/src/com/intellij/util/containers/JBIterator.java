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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.Functions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
 * @see JBIterable#transform(Function)
 * @see JBIterable#filter(Condition)
 * @see TreeTraversal.TracingIt
 *
 * @author gregsh
 *
 * @noinspection unchecked, AssignmentToForLoopParameter
 */
public abstract class JBIterator<E> implements Iterator<E> {
  private static final Object NONE = new String("#none");
  private static final Object STOP = new String("#stop");
  private static final Object SKIP = new String("#skip");

  @NotNull
  public static <E extends JBIterator<?>> JBIterable<E> cursor(@NotNull E iterator) {
    return JBIterable.generate(iterator, Functions.<E, E>identity()).takeWhile(ADVANCE);
  }

  @NotNull
  public static <E> JBIterator<E> from(@NotNull final Iterator<E> it) {
    return it instanceof JBIterator ? (JBIterator<E>)it : new JBIterator<E>() {
      @Override
      protected E nextImpl() {
        return it.hasNext() ? it.next() : stop();
      }
    };
  }

  private Object myCurrent = NONE;
  private Object myNext = NONE;

  private final Op myFirstOp = new Op(null);
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
  @Nullable
  protected final E stop() {
    myNext = STOP;
    return null;
  }

  /**
   * Notifies the iterator to skip and re-invoke nextImpl().
   */
  @Nullable
  protected final E skip() {
    myNext = SKIP;
    return null;
  }

  @Override
  public final boolean hasNext() {
    peekNext();
    return myNext != STOP;
  }

  @Nullable
  @Override
  public final E next() {
    advance();
    return current();
  }

  /**
   * Proceeds to the next element if any and returns true; otherwise false.
   */
  public final boolean advance() {
    myCurrent = NONE;
    peekNext();
    if (myNext == STOP) return false;
    myCurrent = myNext;
    myNext = NONE;
    currentChanged();
    return true;
  }

  /**
   * Returns the current element if any; otherwise throws exception.
   */
  @Nullable
  public final E current() {
    if (myCurrent == NONE) throw new NoSuchElementException();
    return (E)myCurrent;
  }

  private void peekNext() {
    if (myNext != NONE) return;
    Object o = NONE;
    for (Op op = myFirstOp; op != null; op = op == null ? myFirstOp : op.nextOp) {
      o = op == myFirstOp ? nextImpl() : op.apply(o);
      if (myNext == SKIP) {
        o = myNext = NONE;
        op = null;
      }
      if (myNext == STOP) return;
    }
    myNext = o;
  }

  @NotNull
  public final <T> JBIterator<T> transform(@NotNull Function<? super E, T> function) {
    return addOp(new Op<Function<? super E, T>>(function) {
      @Override
      public Object apply(Object o) {
        return impl.fun((E)o);
      }
    });
  }

  @NotNull
  public final JBIterator<E> filter(@NotNull Condition<? super E> condition) {
    return addOp(new Op<Condition<? super E>>(condition) {
      @Override
      public Object apply(Object o) {
        return impl.value((E)o) ? o : skip();
      }
    });
  }

  @NotNull
  public final JBIterator<E> take(int count) {
    return takeWhile(new CountDown<E>(count));
  }

  @NotNull
  public final JBIterator<E> takeWhile(@NotNull Condition<? super E> condition) {
    return addOp(new Op<Condition<? super E>>(condition) {
      @Override
      public Object apply(Object o) {
        return impl.value((E)o) ? o : stop();
      }

      @Override
      public String toString() {
        return "takeWhile:" + super.toString();
      }
    });
  }

  @NotNull
  public final JBIterator<E> skip(int count) {
    return skipWhile(new CountDown<E>(count));
  }

  @NotNull
  public final JBIterator<E> skipWhile(@NotNull final Condition<? super E> condition) {
    return addOp(new Op<Condition<? super E>>(condition) {

      boolean active = true;

      @Override
      public Object apply(Object o) {
        if (active && condition.value((E)o)) return skip();
        active = false;
        return o;
      }

      @Override
      public String toString() {
        return "skipWhile:" + super.toString();
      }
    });
  }

  @NotNull
  private <T> T addOp(@NotNull Op op) {
    myLastOp.nextOp = op;
    myLastOp = myLastOp.nextOp;
    return (T)this;
  }

  @Override
  public final void remove() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public final List<E> toList() {
    return Collections.unmodifiableList(ContainerUtil.newArrayList(JBIterable.once(this)));
  }

  @Override
  public String toString() {
    JBIterable<Op> ops = operationsImpl();
    return "{cur=" + myCurrent + "; next=" + myNext + (ops.isEmpty() ? "" : "; ops[" + ops.size() + "]=" + ops) + "}";
  }

  @NotNull
  public final JBIterable<Function<Object, Object>> getTransformations() {
    return (JBIterable<Function<Object, Object>>)(JBIterable)operationsImpl().transform(new Function<Op, Object>() {
      @Override
      public Object fun(Op op) {
        return op.impl;
      }
    }).filter(Function.class);
  }

  @NotNull
  private JBIterable<Op> operationsImpl() {
    return JBIterable.generate(myFirstOp.nextOp, new Function<Op, Op>() {
      @Override
      public Op fun(Op op) {
        return op.nextOp;
      }
    });
  }

  static String toShortString(@NotNull Object o) {
    String fqn = o.getClass().getName();
    return StringUtil.replace(o.toString(), fqn, StringUtil.getShortName(fqn, '.'));
  }

  private static final Condition<JBIterator<?>> ADVANCE = new Condition<JBIterator<?>>() {
    @Override
    public boolean value(JBIterator<?> it) {
      return it.advance();
    }
  };

  private static class Op<T> {
    final T impl;
    Op nextOp;

    public Op(T impl) {
      this.impl = impl;
    }

    Object apply(Object o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return impl == null ? "" : toShortString(impl);
    }
  }

  private static class CountDown<A> implements Condition<A> {
    int cur;

    public CountDown(int count) {
      cur = count;
    }

    @Override
    public boolean value(A a) {
      return cur > 0 && cur-- != 0;
    }
  }
}
