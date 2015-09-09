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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Iterator that accumulates transformations and filters keeping its instance
 * So (JBIterable#) filter() and transform() preserves custom iterator API.
 * <h3>Supported contracts:</h3>
 * <ul>
 *   <li>Classic iterator: hasNext() / next()</li>
 *   <li>Cursor: advance() / current()</li>
 *   <li>Cursor-like iterable: JBIterable.cursor()</li>
 * </ul>
 *
 * @see JBIterable#transform(Function)
 * @see JBIterable#filter(Condition)
 * @see TreeTraversal.TracingIt
 *
 * @author gregsh
 *
 * @noinspection unchecked
 */
public abstract class JBIterator<E> implements Iterator<E> {
  private static final Object INIT = new String("#init");
  private static final Object STOP = new String("#stop");
  private static final Object SKIP = new String("#skip");

  public static <E extends JBIterator<?>> JBIterable<E> cursor(@NotNull E iterator) {
    return JBIterable.generate(iterator, Functions.<E, E>identity()).takeWhile(ADVANCE);
  }

  public static <E> JBIterator<E> from(final Iterator<E> it) {
    return it instanceof JBIterator ? (JBIterator<E>)it : new JBIterator<E>() {
      @Override
      protected E nextImpl() {
        return it.hasNext() ? it.next() : stop();
      }

      @Override
      public void remove() {
        it.remove();
      }
    };
  }

  private Object cur = INIT;
  private final Op firstOp = new Op(null);
  private Op lastOp = firstOp;

  protected abstract E nextImpl();

  protected final E stop() {
    cur = STOP;
    return null;
  }

  private boolean isStopped() {
    return cur == STOP;
  }

  @Override
  public final boolean hasNext() {
    if (cur == INIT) advanceImpl();
    return cur != STOP;
  }

  @Override
  public final E next() {
    if (cur == INIT) advanceImpl();
    E result = current();
    cur = INIT;
    return result;
  }

  public final boolean advance() {
    if (cur != STOP) advanceImpl();
    return cur != STOP;
  }

  public E current() {
    if (cur == STOP) throw new NoSuchElementException();
    return (E)cur;
  }

  private void advanceImpl() {
    cur = INIT;
    Object o = nextImpl();
    if (isStopped()) return;
    Op op = firstOp.nextOp;
    while (op != null) {
      o = op.apply(o);
      if (isStopped()) return;
      if (o == SKIP) {
        o = nextImpl();
        if (isStopped()) return;
        op = firstOp;
      }
      op = op.nextOp;
    }
    cur = o;
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
        return impl.value((E)o) ? o : SKIP;
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
        if (active && condition.value((E)o)) return SKIP;
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
    lastOp.nextOp = op;
    lastOp = lastOp.nextOp;
    return (T)this;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public final List<E> toList() {
    return Collections.unmodifiableList(ContainerUtil.newArrayList(JBIterable.once(this)));
  }

  @Override
  public String toString() {
    JBIterable<Op> ops = operationsImpl();
    return "{cur=" + cur + "; ops[" + ops.size() + "]=" + ops + "}";
  }

  @NotNull
  public JBIterable<Object> operations() {
    return operationsImpl().transform(new Function<Op, Object>() {
      @Override
      public Object fun(Op op) {
        return op.impl;
      }
    });
  }

  @NotNull
  private JBIterable<Op> operationsImpl() {
    return JBIterable.generate(firstOp.nextOp, new Function<Op, Op>() {
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
