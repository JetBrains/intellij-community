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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
public abstract class RootProviderBaseImpl implements RootProvider {
  private final EventDispatcher<RootSetChangedListener> myDispatcher = EventDispatcher.create(RootSetChangedListener.class);
  @Override
  public void addRootSetChangedListener(@NotNull RootSetChangedListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void removeRootSetChangedListener(@NotNull RootSetChangedListener listener) {
    myDispatcher.removeListener(listener);
  }

  @Override
  public void addRootSetChangedListener(@NotNull RootSetChangedListener listener, @NotNull Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  public void fireRootSetChanged() {
    myDispatcher.getMulticaster().rootSetChanged(this);
  }

}
