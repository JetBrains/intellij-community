package com.intellij.vcs.log.ui.render;

import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.graph.PaintInfo;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CommitCellRender extends AbstractPaddingCellRender {

  public CommitCellRender(@NotNull VcsLogColorManager colorManager, @NotNull VcsLogDataHolder dataHolder) {
    super(colorManager, dataHolder);
  }

  @Nullable
  @Override
  protected PaintInfo getGraphImage(int row) {
    return null;
  }

}
