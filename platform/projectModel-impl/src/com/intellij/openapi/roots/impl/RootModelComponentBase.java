/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;


/**
 *  @author dsl
 */
abstract class RootModelComponentBase implements Disposable {
  @NotNull
  private final RootModelImpl myRootModel;
  private boolean myDisposed;

  RootModelComponentBase(@NotNull RootModelImpl rootModel) {
    rootModel.registerOnDispose(this);
    myRootModel = rootModel;
  }


  @NotNull
  public RootModelImpl getRootModel() {
    return myRootModel;
  }

  @Override
  public void dispose() {
    if (!myDisposed) {
      myRootModel.unregisterOnDispose(this);
      myDisposed = true;
    }
  }

  public boolean isDisposed() {
    return myDisposed;
  }
}
