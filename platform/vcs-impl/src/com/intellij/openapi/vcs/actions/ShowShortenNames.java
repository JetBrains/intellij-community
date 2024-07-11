// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author Konstantin Bulenkov
 */
public final class ShowShortenNames extends ActionGroup implements DumbAware {
  private final AnAction[] myChildren;

  public ShowShortenNames() {
    super(VcsBundle.messagePointer("annotations.short.name.type.group.names"), true);
    final ArrayList<AnAction> kids = new ArrayList<>(ShortNameType.values().length);
    for (ShortNameType type : ShortNameType.values()) {
      kids.add(new SetShortNameTypeAction(type));
    }
    myChildren = kids.toArray(AnAction.EMPTY_ARRAY);
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return myChildren;
  }

  public static ShortNameType getType() {
    for (ShortNameType type : ShortNameType.values()) {
      if (type.isSet()) {
        return type;
      }
    }
    return ShortNameType.LASTNAME;
  }

  private static class SetShortNameTypeAction extends ToggleAction implements DumbAware {
    private final ShortNameType myType;

    SetShortNameTypeAction(ShortNameType type) {
      super(type.getDescription());
      myType = type;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myType == getType();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean enabled) {
      if (enabled) {
        myType.set();
      }

      AnnotateActionGroup.revalidateMarkupInAllEditors();
    }
  }
}
