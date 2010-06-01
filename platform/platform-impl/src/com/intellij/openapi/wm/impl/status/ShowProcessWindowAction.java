/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.status;

import com.intellij.idea.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.wm.ex.*;
import com.intellij.openapi.wm.impl.*;
import org.jetbrains.annotations.*;

import java.awt.*;

public class ShowProcessWindowAction extends ToggleAction implements DumbAware {

  public ShowProcessWindowAction() {
    super(ActionsBundle.message("action.ShowProcessWindow.text"), ActionsBundle.message("action.ShowProcessWindow.description"), null);
  }


  public boolean isSelected(final AnActionEvent e) {
    final IdeFrameImpl frame = getFrame();
    if (frame == null) return false;
    return ((StatusBarEx) frame.getStatusBar()).isProcessWindowOpen();
  }

  public void update(final AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(getFrame() != null);
  }

  @Nullable
  private static IdeFrameImpl getFrame() {
    Container window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    while (window != null) {
      if (window instanceof IdeFrameImpl) return (IdeFrameImpl)window;
      window = window.getParent();
    }

    return null;
  }

  public void setSelected(final AnActionEvent e, final boolean state) {
    final IdeFrameImpl frame = getFrame();
    if (frame == null) return;
    ((StatusBarEx) frame.getStatusBar()).setProcessWindowOpen(state);
  }
}
