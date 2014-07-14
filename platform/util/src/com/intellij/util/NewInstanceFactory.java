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
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Factory;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;

public class NewInstanceFactory<T> implements Factory<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.NewInstanceFactory");
  private final Constructor myConstructor;
  private final Object[] myArgs;

  private NewInstanceFactory(@NotNull Constructor constructor, @NotNull Object[] args) {
    myConstructor = constructor;
    myArgs = args;
  }

  @Override
  public T create() {
    try {
      return (T)myConstructor.newInstance(myArgs);
    }
    catch (Exception e) {
      LOG.error(e);
      return null;
    }
  }

  public static <T> Factory<T> fromClass(@NotNull final Class<T> clazz) {
    try {
      return new NewInstanceFactory<T>(clazz.getConstructor(ArrayUtil.EMPTY_CLASS_ARRAY), ArrayUtil.EMPTY_OBJECT_ARRAY);
    }
    catch (NoSuchMethodException e) {
      return new Factory<T>() {
        @Override
        public T create() {
          try {
            return clazz.newInstance();
          } catch (Exception e) {
            LOG.error(e);
            return null;
          }
        }
      };
    }
  }
}
