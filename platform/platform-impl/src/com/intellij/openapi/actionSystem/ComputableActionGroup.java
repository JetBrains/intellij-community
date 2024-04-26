// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.CachedValueImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** @deprecated Use {@link DefaultActionGroup} or {@link ActionGroup} directly */
@Deprecated(forRemoval = true)
public abstract class ComputableActionGroup extends ActionGroup implements DumbAware {
  private CachedValue<AnAction[]> myChildren;

  protected ComputableActionGroup() {
  }

  protected ComputableActionGroup(boolean popup) {
    super(Presentation.NULL_STRING, popup);
  }

  {
    getTemplatePresentation().setHideGroupIfEmpty(true);
  }

  @Override
  public final AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    if (e == null) {
      return EMPTY_ARRAY;
    }

    if (myChildren == null) {
      myChildren = new CachedValueImpl<>(createChildrenProvider(e.getActionManager()));
    }
    return myChildren.getValue();
  }

  protected abstract @NotNull CachedValueProvider<AnAction[]> createChildrenProvider(@NotNull ActionManager actionManager);

  /** @deprecated Use {@link DefaultActionGroup} or {@link ActionGroup} directly */
  @Deprecated
  public abstract static class Simple extends ComputableActionGroup {
    protected Simple() {
    }

    protected Simple(boolean popup) {
      super(popup);
    }

    @Override
    protected final @NotNull CachedValueProvider<AnAction[]> createChildrenProvider(final @NotNull ActionManager actionManager) {
      return () -> CachedValueProvider.Result.create(computeChildren(actionManager), ModificationTracker.NEVER_CHANGED);
    }

    protected abstract AnAction @NotNull [] computeChildren(@NotNull ActionManager manager);
  }
}