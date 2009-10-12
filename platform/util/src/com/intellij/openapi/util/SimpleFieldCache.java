/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

public abstract class SimpleFieldCache<T, Owner> extends FieldCache<T,Owner,Object, Object>{
  public final T get(Owner owner) {
    return get(null, owner, null);
  }

  protected final T compute(Owner owner, Object p) {
    return compute(owner);
  }

  protected final T getValue(Owner owner, Object p) {
    return getValue(owner);
  }

  protected final void putValue(T t, Owner owner, Object p) {
    putValue(t, owner);
  }

  protected abstract T compute(Owner owner);
  protected abstract T getValue(Owner owner);
  protected abstract void putValue(T t, Owner owner);
}