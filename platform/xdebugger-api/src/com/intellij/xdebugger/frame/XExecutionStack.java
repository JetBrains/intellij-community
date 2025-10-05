// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.NlsContexts.ListItem;
import com.intellij.xdebugger.Obsolescent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a stack of executions frames usually corresponding to a thread. It is shown in 'Frames' panel of
 * 'Debug' tool window
 */
public abstract class XExecutionStack {

  /**
   * Data constant which contains a set of selected stacks on a view. Used to support actions that work on a group of stacks/threads.
   */
  @ApiStatus.Experimental
  public static final DataKey<List<XExecutionStack>> SELECTED_STACKS = DataKey.create("XExecutionStacks");

  public static final XExecutionStack[] EMPTY_ARRAY = new XExecutionStack[0];
  private final @Nls String myDisplayName;
  private Icon myIcon;

  /**
   * @param displayName presentable name of the thread to be shown in the combobox in 'Frames' tab
   */
  protected XExecutionStack(@ListItem String displayName) {
    this(displayName, AllIcons.Debugger.ThreadSuspended);
  }

  /**
   * @param displayName presentable name of the thread to be shown in the combobox in 'Frames' tab
   * @param icon icon to be shown in the combobox in 'Frames' tab
   */
  protected XExecutionStack(@ListItem @NotNull String displayName, final @Nullable Icon icon) {
    myDisplayName = displayName;
    myIcon = icon;
  }

  public final @NotNull @Nls String getDisplayName() {
    return myDisplayName;
  }

  public @Nullable AdditionalDisplayInfo getAdditionalDisplayInfo() {
    return null;
  }

  public final @Nullable Icon getIcon() {
    return myIcon;
  }

  @ApiStatus.Internal
  public final void setIcon(Icon icon) { myIcon = icon; }

  /**
   * Override this method to provide an icon with optional tooltip and popup actions. This icon will be shown on the editor gutter to the
   * left of the execution line when this thread is selected in 'Frames' tab.
   */
  public @Nullable GutterIconRenderer getExecutionLineIconRenderer() {
    return null;
  }

  /**
   * Return top stack frame synchronously
   * @return top stack frame or {@code null} if it isn't available
   */
  public abstract @Nullable XStackFrame getTopFrame();

  /**
   * Start computing stack frames top-down starting from {@code firstFrameIndex}. This method is called from the Event Dispatch Thread
   * so it should return quickly
   * @param firstFrameIndex frame index to start from ({@code 1} corresponds to the frame just under the top frame)
   * @param container callback
   */
  public abstract void computeStackFrames(int firstFrameIndex, XStackFrameContainer container);

  /**
   * Provides additional information about XExecutionStack, which frontend may use.
   */
  @ApiStatus.Internal
  public @Nullable CompletableFuture<XDescriptor> getXExecutionStackDescriptorAsync() {
    return null;
  }

  public interface XStackFrameContainer extends Obsolescent, XValueCallback {
    /**
     * Add stack frames to the list
     * @param stackFrames stack frames to add
     * @param last {@code true} if all frames are added
     */
    void addStackFrames(@NotNull List<? extends XStackFrame> stackFrames, boolean last);
  }

  public static class AdditionalDisplayInfo {
    private final @Nls @NotNull String myText;
    private final @Nls String myTooltip;

    public AdditionalDisplayInfo(@Nls @NotNull String text, @Nls @Nullable String tooltip) {

      myText = text;
      myTooltip = tooltip;
    }

    public @Nls @NotNull String getText() {
      return myText;
    }

    public @Nls @Nullable String getTooltip() {
      return myTooltip;
    }
  }
}
