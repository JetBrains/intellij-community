// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions.obsolete;

import com.intellij.ide.actions.NonTrivialActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ObsoleteNewActionsGroup extends NonTrivialActionGroup {
  @SuppressWarnings("UnnecessarilyQualifiedStaticUsage")
  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    if (!Registry.is("devkit.obsolete.new.file.actions.enabled")) return AnAction.EMPTY_ARRAY;

    return super.getChildren(e);
  }
}
