/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.util.ThrowableComputable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/30/12
 * Time: 8:19 PM
 */
public class ThreadSafeTransparentlyFailedValue<T,E extends Exception> implements TransparentlyFailedValueI<T,E> {
  private final AtomicReference<ThrowableComputable<T, E>> myRef;

  public ThreadSafeTransparentlyFailedValue() {
    myRef = new AtomicReference<>();
  }

  @Override
  public void set(T t) {
    if (t != null) {
      myRef.set(new Value<>(t));
    }
  }

  @Override
  public void fail(E e) {
    myRef.set(new ExceptionHolder<>(e));
  }

  @Override
  public void failRuntime(RuntimeException e) {
    myRef.set(new RuntimeExceptionHolder<>(e));
  }

  @Override
  public T get() throws E {
    return myRef.get() == null ? null : myRef.get().compute();
  }

  @Override
  public boolean haveSomething() {       // todo correct here
    return myRef.get() != null;
  }

  private static class Value<T,E extends Exception> implements ThrowableComputable<T,E> {
    private final T myT;

    private Value(final T t) {
      myT = t;
    }

    @Override
    public T compute() throws E {
      return myT;
    }
  }

  private static class ExceptionHolder<T,E extends Exception> implements ThrowableComputable<T,E> {
    private final E myE;

    private ExceptionHolder(E e) {
      myE = e;
    }

    @Override
    public T compute() throws E {
      throw myE;
    }
  }

  private static class RuntimeExceptionHolder<T,E extends Exception> implements ThrowableComputable<T,E> {
    private final RuntimeException myException;

    private RuntimeExceptionHolder(RuntimeException exception) {
      myException = exception;
    }

    @Override
    public T compute() throws E {
      throw myException;
    }
  }
}
