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
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class DiffRequestPresentableProxy implements DiffRequestPresentable {
  private DiffRequestPresentable myDelegate;
  private List<? extends AnAction> myCachedActions;
  private MyResult myStepResult;

  @Nullable
  protected abstract DiffRequestPresentable init();

  @Nullable
  private DiffRequestPresentable initRequest() {
    if (myDelegate == null) {
      myDelegate = init();
    }
    return myDelegate;
  }

  public List<? extends AnAction> createActions(ShowDiffAction.DiffExtendUIFactory uiFactory) {
    if (myCachedActions == null) {
      myCachedActions = initRequest().createActions(uiFactory);
    }
    return myCachedActions;
  }

  public MyResult step(DiffChainContext context) {
    final DiffRequestPresentable request = initRequest();
    if (request == null) {
      return new MyResult(null, DiffPresentationReturnValue.removeFromList);
    }
    myStepResult = request.step(context);
    return myStepResult;
  }

  public boolean haveStuff() {
    final DiffRequestPresentable request = initRequest();
    if (request == null) {
      return false;
    }
    return request.haveStuff();
  }
}
