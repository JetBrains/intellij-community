/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

/**
 * @author irengrig
 *         Date: 7/5/11
 *         Time: 3:35 PM
 */
public class TransparentlyFailedValue<T, E extends Exception> implements TransparentlyFailedValueI<T,E> {
  private T t;
  private E e;
  private RuntimeException myRuntime;

  @Override
  public void set(final T t) {
    this.t = t;
  }

  @Override
  public void fail(final E e) {
    this.e = e;
  }

  @Override
  public void failRuntime(final RuntimeException e) {
    myRuntime = e;
  }

  @Override
  public T get() throws E {
    if (this.e != null) throw this.e;
    if (myRuntime != null) throw myRuntime;
    return this.t;
  }

  public void take(final TransparentlyFailedValue<T,E> value) {
    this.t = value.t;
    this.e = value.e;
    myRuntime = value.myRuntime;
  }

  @Override
  public boolean haveSomething() {
    return e != null || myRuntime != null || t != null;
  }
}
