/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 26, 2007
 * Time: 3:52:05 PM
 */
package com.intellij.util.concurrency;

import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class AtomicFieldUpdater<T,V> {
  private static final Unsafe unsafe = getUnsafe();

  @NotNull
  public static Unsafe getUnsafe() {
    Unsafe unsafe = null;
    Class uc = Unsafe.class;
    try {
      Field[] fields = uc.getDeclaredFields();
      for (Field field : fields) {
        if (field.getName().equals("theUnsafe")) {
          field.setAccessible(true);
          unsafe = (Unsafe)field.get(uc);
          break;
        }
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    if (unsafe == null) {
      throw new RuntimeException("Could not find 'theUnsafe' field in the " + uc);
    }
    return unsafe;
  }

  private final long offset;

  @NotNull
  public static <T, V> AtomicFieldUpdater<T, V> forFieldOfType(@NotNull Class<T> ownerClass, @NotNull Class<V> fieldType) {
    return new AtomicFieldUpdater<T, V>(ownerClass, fieldType);
  }

  @NotNull
  public static <T> AtomicFieldUpdater<T, Long> forLongField(@NotNull Class<T> ownerClass) {
    return new AtomicFieldUpdater<T, Long>(ownerClass, long.class);
  }

  private AtomicFieldUpdater(@NotNull Class<T> ownerClass, @NotNull Class<V> fieldType) {
    Field[] declaredFields = ownerClass.getDeclaredFields();
    Field found = null;
    for (Field field : declaredFields) {
      if ((field.getModifiers() & (Modifier.STATIC | Modifier.FINAL)) != 0) {
        continue;
      }
      if (fieldType.isAssignableFrom(field.getType())) {
        if (found == null) {
          found = field;
        }
        else {
          throw new IllegalArgumentException("Two fields of "+fieldType+" found in the "+ownerClass+": "+found.getName() + " and "+field.getName());
        }
      }
    }
    if (found == null) {
      throw new IllegalArgumentException("No (non-static, non-final) field of "+fieldType+" found in the "+ownerClass);
    }
    found.setAccessible(true);
    if ((found.getModifiers() & Modifier.VOLATILE) == 0) {
      throw new IllegalArgumentException("Field "+found+" in the "+ownerClass+" must be volatile");
    }
    offset = unsafe.objectFieldOffset(found);
  }

  public boolean compareAndSet(@NotNull T owner, V expected, V newValue) {
    return unsafe.compareAndSwapObject(owner, offset, expected, newValue);
  }

  public boolean compareAndSetLong(@NotNull T owner, long expected, long newValue) {
    return unsafe.compareAndSwapLong(owner, offset, expected, newValue);
  }

  public void set(@NotNull T owner, V newValue) {
    unsafe.putObjectVolatile(owner, offset, newValue);
  }

  public V get(@NotNull T owner) {
    //noinspection unchecked
    return (V)unsafe.getObjectVolatile(owner, offset);
  }
}
