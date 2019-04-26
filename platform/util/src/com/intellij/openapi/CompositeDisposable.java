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
 * Like {@link Disposable}, you register instance of this class in {@link Disposer}.
 * Call {@link #add(Disposable)} to request automatic disposal of additional objects.
 * Comparing to registering these additional disposables with Disposer one by one,
 * this class improves on the memory usage by not creating temporary objects inside Disposer.
 */
public class CompositeDisposable implements Disposable {
  private final List<Disposable> myDisposables = new ArrayList<>();
  private boolean disposed;
  private boolean isDisposing;

  public void add(@NotNull Disposable disposable) {
    assert !disposed : "Already disposed";
    myDisposables.add(disposable);
  }

  /**
   * Removes the given disposable from this composite. Should be called when the disposable is disposed independently
   * from the CompositeDisposable to prevent the CompositeDisposable from holding references to disposed objects.
   * This method is safe to call while this CompositeDisposable is being disposed.
   *
   * @param disposable the disposable to remove from this CompositeDisposable
   */
  public void remove(@NotNull Disposable disposable) {
    // If isDisposing is true, there is no point of modifying the myDisposables list since it's going to be cleared anyway.
    if (!isDisposing) {
      for (int i = myDisposables.size() - 1; i >= 0; i--) {
        Disposable d = myDisposables.get(i);
        if (d == disposable) { // Compare by identity.
          myDisposables.remove(i);
        }
      }
    }
  }

  @Override
  public void dispose() {
    //assert !disposed : "Already disposed";

    isDisposing = true;

    for (int i = myDisposables.size() - 1; i >= 0; i--) {
      Disposable disposable = myDisposables.get(i);
      Disposer.dispose(disposable);
    }

    isDisposing = false;
    myDisposables.clear();
    disposed = true;
  }
}
