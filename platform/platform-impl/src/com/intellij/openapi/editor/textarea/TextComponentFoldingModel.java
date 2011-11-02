package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 11/2/11 6:13 PM
 */
public class TextComponentFoldingModel implements FoldingModel {

  @Override
  public FoldRegion addFoldRegion(int startOffset, int endOffset, @NotNull String placeholderText) {
    return null;
  }

  @Override
  public boolean addFoldRegion(@NotNull FoldRegion region) {
    return false;
  }

  @Override
  public void removeFoldRegion(@NotNull FoldRegion region) {
  }

  @NotNull
  @Override
  public FoldRegion[] getAllFoldRegions() {
    return FoldRegion.EMPTY_ARRAY;
  }

  @Override
  public boolean isOffsetCollapsed(int offset) {
    return false;
  }

  @Override
  public FoldRegion getCollapsedRegionAtOffset(int offset) {
    return null;
  }

  @Override
  public void runBatchFoldingOperation(@NotNull Runnable operation) {
  }

  @Override
  public void runBatchFoldingOperationDoNotCollapseCaret(@NotNull Runnable operation) {
  }
}
