// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table;

import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.vcs.log.impl.VcsLogIcons;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;

@ApiStatus.Internal
public class TableWithProgress extends JBTable {
  public TableWithProgress(@NotNull TableModel model) {
    super(model);
  }

  @Override
  protected @NotNull AsyncProcessIcon createBusyIcon() {
    return new LastRowLoadingIcon();
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    if (isBusy()) {
      return new Dimension(size.width, size.height + myBusyIcon.getPreferredSize().height);
    }
    return size;
  }

  protected boolean isBusy() {
    return myBusyIcon != null && myBusyIcon.isRunning();
  }

  @Override
  protected void paintComponent(@NotNull Graphics g) {
    super.paintComponent(g);
    if (isBusy()) {
      int preferredHeight = super.getPreferredSize().height;
      paintFooter(g, 0, preferredHeight, getWidth(), getHeight() - preferredHeight);
    }
  }

  protected void paintFooter(@NotNull Graphics g, int x, int y, int width, int height) {
    g.setColor(getBackground());
    g.fillRect(x, y, width, height);
  }

  private class LastRowLoadingIcon extends AsyncProcessIcon {
    LastRowLoadingIcon() {
      super(TableWithProgress.this.toString(),
            new Icon[]{VcsLogIcons.Process.Dots_2, VcsLogIcons.Process.Dots_3, VcsLogIcons.Process.Dots_4, VcsLogIcons.Process.Dots_5},
            VcsLogIcons.Process.Dots_1);
    }

    @Override
    protected @NotNull Rectangle calculateBounds(@NotNull JComponent container) {
      Dimension iconSize = getPreferredSize();
      return new Rectangle((container.getWidth() - iconSize.width) / 2, container.getPreferredSize().height - iconSize.height,
                           iconSize.width,
                           iconSize.height);
    }
  }
}
