/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;

/**
 * @author peter
 */
public abstract class LazyInstance<T> extends NotNullLazyValue<T>{
  
  protected abstract Class<T> getInstanceClass() throws ClassNotFoundException;

  @Override
  @NotNull
  protected final T compute() {
    try {
      Class<T> tClass = getInstanceClass();
      Constructor<T> constructor = tClass.getConstructor();
      constructor.setAccessible(true);
      return tClass.newInstance();
    }
    catch (InstantiationException e) {
      throw new RuntimeException(e);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
    catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }
}
