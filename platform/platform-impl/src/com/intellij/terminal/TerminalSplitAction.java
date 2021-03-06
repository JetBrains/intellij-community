// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.idea.ActionsBundle;
import com.intellij.util.ui.UIUtil;
import com.jediterm.terminal.ui.TerminalAction;
import com.jediterm.terminal.ui.TerminalActionPresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.Objects;

public abstract class TerminalSplitAction extends TerminalAction {

  private final boolean myVertically;

  private TerminalSplitAction(@NotNull TerminalActionPresentation presentation, boolean vertically) {
    super(presentation);
    myVertically = vertically;
  }

  @Override
  public boolean actionPerformed(@Nullable KeyEvent e) {
    split(myVertically);
    return true;
  }

  public abstract void split(boolean vertically);

  public static TerminalSplitAction create(boolean vertically, @Nullable JBTerminalWidgetListener listener) {
    String text = vertically ? UIUtil.removeMnemonic(ActionsBundle.message("action.SplitVertically.text"))
                             : UIUtil.removeMnemonic(ActionsBundle.message("action.SplitHorizontally.text"));
    return new TerminalSplitAction(new TerminalActionPresentation(text, Collections.emptyList()), vertically) {
      @Override
      public boolean isEnabled(@Nullable KeyEvent e) {
        return listener != null && listener.canSplit(vertically);
      }

      @Override
      public void split(boolean vertically) {
        Objects.requireNonNull(listener).split(vertically);
      }
    };
  }
}
