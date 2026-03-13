// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame;

import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.frame.XStackFrame;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.List;
import java.util.Optional;

/**
 * Synthetic frame which encapsulates hidden library frames as a single fold.
 *
 * @see XFramesView#shouldFoldHiddenFrames()
 */
@ApiStatus.Internal
public class HiddenStackFramesItem extends XStackFrame implements XStackFrameWithCustomBackgroundColor,
                                                                  XStackFrameWithSeparatorAbove,
                                                                  HiddenFramesStackFrame {
  final List<XStackFrame> hiddenFrames;

  public HiddenStackFramesItem(List<XStackFrame> hiddenFrames) {
    this.hiddenFrames = List.copyOf(hiddenFrames);
    if (hiddenFrames.isEmpty()) {
      throw new IllegalArgumentException();
    }
  }

  @Override
  public void customizePresentation(@NotNull ColoredTextContainer component) {
    component.append(XDebuggerBundle.message("label.folded.frames", hiddenFrames.size()), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    component.setIcon(EmptyIcon.ICON_16);
  }

  @Override
  public @Nullable Color getBackgroundColor() {
    return null;
  }

  @Override
  public @NotNull List<XStackFrame> getHiddenFrames() {
    return hiddenFrames;
  }

  private Optional<XStackFrameWithSeparatorAbove> findFrameWithSeparator() {
    // We check only the first frame; otherwise, it's not clear what to do.
    // Might be reconsidered in the future.
    return hiddenFrames.get(0) instanceof XStackFrameWithSeparatorAbove frame
           ? Optional.of(frame)
           : Optional.empty();
  }

  @Override
  public boolean hasSeparatorAbove() {
    return findFrameWithSeparator().map(XStackFrameWithSeparatorAbove::hasSeparatorAbove).orElse(false);
  }

  @Override
  public String getCaptionAboveOf() {
    return findFrameWithSeparator().map(XStackFrameWithSeparatorAbove::getCaptionAboveOf).orElse(null);
  }
}
