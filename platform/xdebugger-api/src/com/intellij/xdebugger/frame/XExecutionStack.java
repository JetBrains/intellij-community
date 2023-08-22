// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.frame;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.NlsContexts.ListItem;
import com.intellij.xdebugger.Obsolescent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Represents a stack of executions frames usually corresponding to a thread. It is shown in 'Frames' panel of
 * 'Debug' tool window
 */
public abstract class XExecutionStack {
  public static final XExecutionStack[] EMPTY_ARRAY = new XExecutionStack[0];
  private final @Nls String myDisplayName;
  private final Icon myIcon;

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

  @NotNull
  @Nls
  public final String getDisplayName() {
    return myDisplayName;
  }

  @Nullable
  public final Icon getIcon() {
    return myIcon;
  }

  /**
   * Override this method to provide an icon with optional tooltip and popup actions. This icon will be shown on the editor gutter to the
   * left of the execution line when this thread is selected in 'Frames' tab.
   */
  @Nullable
  public GutterIconRenderer getExecutionLineIconRenderer() {
    return null;
  }

  /**
   * Return top stack frame synchronously
   * @return top stack frame or {@code null} if it isn't available
   */
  @Nullable
  public abstract XStackFrame getTopFrame();

  /**
   * Start computing stack frames top-down starting from {@code firstFrameIndex}. This method is called from the Event Dispatch Thread
   * so it should return quickly
   * @param firstFrameIndex frame index to start from ({@code 1} corresponds to the frame just under the top frame)
   * @param container callback
   */
  public abstract void computeStackFrames(int firstFrameIndex, XStackFrameContainer container);

  public interface XStackFrameContainer extends Obsolescent, XValueCallback {
    /**
     * Add stack frames to the list
     * @param stackFrames stack frames to add
     * @param last {@code true} if all frames are added
     */
    void addStackFrames(@NotNull List<? extends XStackFrame> stackFrames, boolean last);
  }
}
