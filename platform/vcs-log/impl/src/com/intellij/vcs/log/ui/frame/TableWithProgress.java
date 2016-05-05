/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.frame;

import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;

public class TableWithProgress extends JBTable {
  public TableWithProgress(@NotNull TableModel model) {
    super(model);
  }

  @NotNull
  @Override
  protected AsyncProcessIcon createBusyIcon() {
    return new LastRowLoadingIcon();
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    if (myBusyIcon != null && myBusyIcon.isRunning()) {
      return new Dimension(size.width, size.height + myBusyIcon.getPreferredSize().height);
    }
    return size;
  }

  private class LastRowLoadingIcon extends AsyncProcessIcon {
    public LastRowLoadingIcon() {
      super(TableWithProgress.this.toString());
      setUseMask(false);
    }

    @NotNull
    @Override
    protected Rectangle calculateBounds(@NotNull JComponent container) {
      Rectangle rec = container.getVisibleRect();
      Dimension iconSize = getPreferredSize();
      return new Rectangle(rec.x + (rec.width - iconSize.width) / 2, rec.y + rec.height - iconSize.height, iconSize.width, iconSize.height);
    }
  }
}
