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
package com.intellij.openapi.util;

import com.intellij.reference.SoftReference;
import com.intellij.util.Function;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

/**
 * @author max
 */
@SuppressWarnings("unchecked")
public class Conditions {
  private Conditions() { }

  public final static Condition<Object> TRUE = Condition.TRUE;
  public final static Condition<Object> FALSE = Condition.FALSE;

  public static <T> Condition<T> alwaysTrue() {
    return (Condition<T>)TRUE;
  }

  public static <T> Condition<T> alwaysFalse() {
    return (Condition<T>)FALSE;
  }

  public static <T> Condition<T> notNull() {
    return (Condition<T>)Condition.NOT_NULL;
  }

  public static <T> Condition<T> constant(boolean value) {
    return (Condition<T>)(value ? TRUE : FALSE);
  }

  public static <T> Condition<T> instanceOf(final Class<?> clazz) {
    return new Condition<T>() {
      public boolean value(T t) {
        return clazz.isInstance(t);
      }
    };
  }

  public static <T> Condition<T> notInstanceOf(final Class<?> clazz) {
    return new Condition<T>() {
      public boolean value(T t) {
        return !clazz.isInstance(t);
      }
    };
  }

  public static Condition<Class> assignableTo(final Class clazz) {
    return new Condition<Class>() {
      public boolean value(Class t) {
        return clazz.isAssignableFrom(t);
      }
    };
  }

  public static <T> Condition<T> instanceOf(final Class<?>... clazz) {
    return new Condition<T>() {
      public boolean value(T t) {
        for (Class<?> aClass : clazz) {
          if (aClass.isInstance(t)) return true;
        }
        return false;
      }
    };
  }

  public static <T> Condition<T> is(final T option) {
    return equalTo(option);
  }

  public static <T> Condition<T> equalTo(final Object option) {
    return new Condition<T>() {
      public boolean value(T t) {
        return Comparing.equal(t, option);
      }
    };
  }

  public static <T> Condition<T> notEqualTo(final Object option) {
    return new Condition<T>() {
      public boolean value(T t) {
        return !Comparing.equal(t, option);
      }
    };
  }

  public static <T> Condition<T> oneOf(T... options) {
    return oneOf(Arrays.asList(options));
  }

  public static <T> Condition<T> oneOf(final Collection<? extends T> options) {
    return new Condition<T>() {
      public boolean value(T t) {
        return options.contains(t);
      }
    };
  }

  public static <T> Condition<T> not(Condition<T> c) {
    if (c == TRUE) return alwaysFalse();
    if (c == FALSE) return alwaysTrue();
    if (c instanceof Not) return ((Not)c).c;
    return new Not<T>(c);
  }

  public static <T> Condition<T> and(Condition<T> c1, Condition<T> c2) {
    return and2(c1, c2);
  }

  public static <T> Condition<T> and2(Condition<? super T> c1, Condition<? super T> c2) {
    if (c1 == TRUE || c2 == FALSE) return (Condition<T>)c2;
    if (c2 == TRUE || c1 == FALSE) return (Condition<T>)c1;
    return new And<T>(c1, c2);
  }

  public static <T> Condition<T> or(Condition<T> c1, Condition<T> c2) {
    return or2(c1, c2);
  }

  public static <T> Condition<T> or2(Condition<? super T> c1, Condition<? super T> c2) {
    if (c1 == FALSE || c2 == TRUE) return (Condition<T>)c2;
    if (c2 == FALSE || c1 == TRUE) return (Condition<T>)c1;
    return new Or<T>(c1, c2);
  }

  public static <A, B> Condition<A> compose(final Function<? super A, B> fun, final Condition<? super B> condition) {
    return new Condition<A>() {
      public boolean value(A o) {
        return condition.value(fun.fun(o));
      }
    };
  }

  public static <T> Condition<T> cached(Condition<T> c) {
    return new SoftRefCache<T>(c);
  }

  private static class Not<T> implements Condition<T> {
    final Condition<T> c;

    Not(Condition<T> c) {
      this.c = c;
    }

    public boolean value(T value) {
      return !c.value(value);
    }
  }

  private static class And<T> implements Condition<T> {
    final Condition<? super T> c1;
    final Condition<? super T> c2;

    And(Condition<? super T> c1, Condition<? super T> c2) {
      this.c1 = c1;
      this.c2 = c2;
    }

    public boolean value(T object) {
      return c1.value(object) && c2.value(object);
    }
  }

  private static class Or<T> implements Condition<T> {
    final Condition<? super T> c1;
    final Condition<? super T> c2;

    Or(Condition<? super T> c1, Condition<? super T> c2) {
      this.c1 = c1;
      this.c2 = c2;
    }

    public boolean value(T object) {
      return c1.value(object) || c2.value(object);
    }
  }

  private static class SoftRefCache<T> implements Condition<T> {
    private final HashMap<Integer, Pair<SoftReference<T>, Boolean>> myCache = new HashMap<Integer, Pair<SoftReference<T>, Boolean>>();
    private final Condition<T> myCondition;

    public SoftRefCache(Condition<T> condition) {
      myCondition = condition;
    }

    public final boolean value(T object) {
      final int key = object.hashCode();
      final Pair<SoftReference<T>, Boolean> entry = myCache.get(key);
      if (entry == null || entry.first.get() != object) {
        boolean value = myCondition.value(object);
        myCache.put(key, Pair.create(new SoftReference<T>(object), value));
        return value;
      }
      else {
        return entry.second;
      }
    }
  }
}