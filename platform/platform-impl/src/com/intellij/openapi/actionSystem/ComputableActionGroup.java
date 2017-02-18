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
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.CachedValueImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ComputableActionGroup extends ActionGroup implements DumbAware {
  private CachedValue<AnAction[]> myChildren;

  protected ComputableActionGroup() {
  }

  protected ComputableActionGroup(boolean popup) {
    super(null, popup);
  }

  @Override
  public boolean hideIfNoVisibleChildren() {
    return true;
  }

  @Override
  @NotNull
  public final AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) {
      return EMPTY_ARRAY;
    }

    if (myChildren == null) {
      myChildren = new CachedValueImpl<>(createChildrenProvider(e.getActionManager()));
    }
    return myChildren.getValue();
  }

  @NotNull
  protected abstract CachedValueProvider<AnAction[]> createChildrenProvider(@NotNull ActionManager actionManager);

  public abstract static class Simple extends ComputableActionGroup {
    protected Simple() {
    }

    protected Simple(boolean popup) {
      super(popup);
    }

    @NotNull
    @Override
    protected final CachedValueProvider<AnAction[]> createChildrenProvider(@NotNull final ActionManager actionManager) {
      return () -> CachedValueProvider.Result.create(computeChildren(actionManager), ModificationTracker.NEVER_CHANGED);
    }

    @NotNull
    protected abstract AnAction[] computeChildren(@NotNull ActionManager manager);
  }
}