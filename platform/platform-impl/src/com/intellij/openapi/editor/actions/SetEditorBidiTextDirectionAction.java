// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.BidiTextDirection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class SetEditorBidiTextDirectionAction extends ToggleAction {
  private final BidiTextDirection myDirection;

  private SetEditorBidiTextDirectionAction(BidiTextDirection direction) {
    myDirection = direction;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return EditorSettingsExternalizable.getInstance().getBidiTextDirection() == myDirection;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    if (myDirection != EditorSettingsExternalizable.getInstance().getBidiTextDirection()) {
      EditorSettingsExternalizable.getInstance().setBidiTextDirection(myDirection);
      EditorFactory.getInstance().refreshAllEditors();
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  public static final class ContentBased extends SetEditorBidiTextDirectionAction {
    public ContentBased() {
      super(BidiTextDirection.CONTENT_BASED);
    }
  }

  public static final class Ltr extends SetEditorBidiTextDirectionAction {
    public Ltr() {
      super(BidiTextDirection.LTR);
    }
  }

  public static final class Rtl extends SetEditorBidiTextDirectionAction {
    public Rtl() {
      super(BidiTextDirection.RTL);
    }
  }
}
