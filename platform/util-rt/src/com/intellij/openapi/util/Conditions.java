/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

/**
 * @author max
 */
public class Conditions {
  private Conditions() {
  }

  @NotNull
  public static <T> Condition<T> alwaysTrue() {
    return (Condition<T>)TRUE;
  }

  @NotNull
  public static <T> Condition<T> alwaysFalse() {
    return (Condition<T>)FALSE;
  }

  public static <T> Condition<T> instanceOf(final Class<?> clazz) {
    return new Condition<T>() {
      @Override
      public boolean value(T t) {
        return clazz.isInstance(t);
      }
    };
  }

  public static Condition<Class> assignableTo(final Class clazz) {
    return new Condition<Class>() {
      @Override
      public boolean value(Class t) {
        return clazz.isAssignableFrom(t);
      }
    };
  }

  public static <T> Condition<T> instanceOf(final Class<?>... clazz) {
    return new Condition<T>() {
      @Override
      public boolean value(T t) {
        for (Class<?> aClass : clazz) {
          if (aClass.isInstance(t)) return true;
        }
        return false;
      }
    };
  }

  public static <T> Condition<T> is(final T option) {
    return new Condition<T>() {
      @Override
      public boolean value(T t) {
        return Comparing.equal(t, option);
      }
    };
  }

  public static <T> Condition<T> oneOf(final T... options) {
    return new Condition<T>() {
      @Override
      public boolean value(T t) {
        return ArrayUtilRt.find(options, t) >= 0;
      }
    };
  }

  public static <T> Condition<T> oneOf(final Iterable<? extends T> options) {
    return new Condition<T>() {
      @Override
      public boolean value(T t) {
        for (T option : options) {
          if (Comparing.equal(option, t)) return true;
        }
        return false;
      }
    };
  }

  public static <T> Condition<T> not(Condition<T> c) {
    return new Not<T>(c);
  }

  public static <T> Condition<T> and(Condition<T> c1, Condition<T> c2) {
    return new And<T>(c1, c2);
  }

  public static <T> Condition<T> and2(Condition<? super T> c1, Condition<? super T> c2) {
    if (c1 == alwaysTrue() || c2 == alwaysFalse()) return (Condition<T>)c2;
    if (c2 == alwaysTrue() || c1 == alwaysFalse()) return (Condition<T>)c1;
    return new And<T>(c1, c2);
  }

  public static <T> Condition<T> or(Condition<T> c1, Condition<T> c2) {
    return new Or<T>(c1, c2);
  }

  public static <T> Condition<T> or2(Condition<? super T> c1, Condition<? super T> c2) {
    if (c1 == alwaysFalse() || c2 == alwaysTrue()) return (Condition<T>)c2;
    if (c2 == alwaysFalse() || c1 == alwaysFalse()) return (Condition<T>)c1;
    return new Or<T>(c1, c2);
  }

  public static <A, B> Condition<A> compose(final Function<? super A, B> fun, final Condition<? super B> condition) {
    return new Condition<A>() {
      @Override
      public boolean value(A o) {
        return condition.value(fun.fun(o));
      }
    };
  }

  public static <T> Condition<T> cached(Condition<T> c) {
    return new SoftRefCache<T>(c);
  }

  private static class Not<T> implements Condition<T> {
    private final Condition<T> myCondition;

    public Not(Condition<T> condition) {
      myCondition = condition;
    }

    @Override
    public boolean value(T value) {
      return !myCondition.value(value);
    }
  }

  private static class And<T> implements Condition<T> {
    private final Condition<? super T> t1;
    private final Condition<? super T> t2;

    public And(Condition<? super T> t1, Condition<? super T> t2) {
      this.t1 = t1;
      this.t2 = t2;
    }

    @Override
    public boolean value(final T object) {
      return t1.value(object) && t2.value(object);
    }
  }

  private static class Or<T> implements Condition<T> {
    private final Condition<? super T> t1;
    private final Condition<? super T> t2;

    public Or(Condition<? super T> t1, Condition<? super T> t2) {
      this.t1 = t1;
      this.t2 = t2;
    }

    @Override
    public boolean value(final T object) {
      return t1.value(object) || t2.value(object);
    }
  }

  public static Condition<Object> TRUE = new Condition<Object>() {
    @Override
    public boolean value(final Object object) {
      return true;
    }
  };
  public static Condition<Object> FALSE = new Condition<Object>() {
    @Override
    public boolean value(final Object object) {
      return false;
    }
  };

  private static class SoftRefCache<T> implements Condition<T> {
    private final HashMap<Integer, Pair<SoftReference<T>, Boolean>> myCache = new HashMap<Integer, Pair<SoftReference<T>, Boolean>>();
    private final Condition<T> myCondition;

    public SoftRefCache(Condition<T> condition) {
      myCondition = condition;
    }

    @Override
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
