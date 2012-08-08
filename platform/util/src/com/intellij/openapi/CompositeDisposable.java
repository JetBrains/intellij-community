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
package com.intellij.openapi;

import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Like {@link com.intellij.openapi.Disposable}, you register instance of this class in {@link com.intellij.openapi.util.Disposer}.
 * Call {@link #add(Disposable)} to request automatic disposal of additional objects.
 * Comparing to registering these additional disposables with Disposer one by one,
 * this class improves on the memory usage by not creating temporary objects inside Disposer.
 */
public class CompositeDisposable implements Disposable {
  private final List<Disposable> myDisposables = new ArrayList<Disposable>();
  private boolean disposed;

  public void add(@NotNull Disposable disposable) {
    assert !disposed : "Already disposed";
    myDisposables.add(disposable);
  }

  @Override
  public void dispose() {
    //assert !disposed : "Already disposed";

    for (int i = myDisposables.size() - 1; i >= 0; i--) {
      Disposable disposable = myDisposables.get(i);
      Disposer.dispose(disposable);
    }
    myDisposables.clear();
    disposed = true;
  }
}
