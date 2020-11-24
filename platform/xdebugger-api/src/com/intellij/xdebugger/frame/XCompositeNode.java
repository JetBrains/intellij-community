// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.frame;

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.Obsolescent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Represents a node with children in a debugger tree. This interface isn't supposed to be implemented by a plugin.
 *
 * @see XValueContainer
 */
public interface XCompositeNode extends Obsolescent {
  /**
   * If node has more children than this constant it's recommended to stop adding children and call {@link #tooManyChildren(int)} method
   */
  int MAX_CHILDREN_TO_SHOW = 100;

  /**
   * Add children to the node.
   * @param children child nodes to add
   * @param last {@code true} if all children added
   */
  void addChildren(@NotNull XValueChildrenList children, final boolean last);

  /**
   * @deprecated use {@link #tooManyChildren(int, Runnable)}
   */
  @Deprecated
  void tooManyChildren(int remaining);

  /**
   * Add an ellipsis node ("...") indicating that the node has too many children.
   * When the user double-clicks the node, the supplied Runnable is called to add the next batch of children.
   * @param remaining number of remaining children or {@code -1} if unknown. This number is used only in the UI.
   * @param addNextChildren Runnable that is used to add the next batch of children.
   *                        Note that it is called from the Event Dispatch thread so it should return quickly.
   * @see #MAX_CHILDREN_TO_SHOW
   */
  default void tooManyChildren(int remaining, @NotNull Runnable addNextChildren) {
    tooManyChildren(remaining);
  }

  /**
   * Use sort specified in data view settings (alreadySorted false, by default) or not
   */
  void setAlreadySorted(boolean alreadySorted);

  /**
   * Indicates that an error occurs
   * @param errorMessage message describing the error
   */
  void setErrorMessage(@NotNull String errorMessage);

  /**
   * Indicates that an error occurs
   * @param errorMessage message describing the error
   * @param link describes a hyperlink which will be appended to the error message
   */
  void setErrorMessage(@NotNull String errorMessage, @Nullable XDebuggerTreeNodeHyperlink link);

  void setMessage(@NotNull String message, final @Nullable Icon icon, final @NotNull SimpleTextAttributes attributes, @Nullable XDebuggerTreeNodeHyperlink link);
}
