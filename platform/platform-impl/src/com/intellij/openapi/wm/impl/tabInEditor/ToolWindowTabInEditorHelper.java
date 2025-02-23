// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.wm.ToolWindow;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The interface manages the interaction between a {@link ToolWindow}
 * and the {@link ToolWindowTabInEditorAction} in the UI context.
 * <p>
 * Implementations of this interface allow custom handling and
 * presentation of {@link ToolWindowTabInEditorAction} for any {@link ToolWindow}s in the IDE.
 * <p>
 * The default implementation, {@link ToolWindowTabInEditorDefaultHelper}, handles the default processing. If the default
 * functionality of transferring the contents of the tool window to the editor tabs doesn't meet specific requirements,
 * a reject handler can be registered:
 * <pre><code>
 * class MyToolWindowHelper: ToolWindowTabInEditorHelper {
 *     override fun updatePresentation(e: AnActionEvent,
 *                                     toolWindow: ToolWindow,
 *                                     tabEditorFile: ToolWindowTabFileImpl?) {
 *         e.presentation.isEnabledAndVisible = false
 *     }
 * }
 *
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 *   ...
 *   &lt;toolWindowTabInEditorHelper implementationClass="com.company.MyToolWindowHelper" key="MyToolWindowId"/&gt;
 *   ...
 * &lt;/extensions&gt;
 * </code></pre>
 *
 * <b>Note:</b> This API is marked as experimental and internal, and its usage or behavior might change in future versions.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public interface ToolWindowTabInEditorHelper {
  /**
   * Updates the presentation of the {@link ToolWindowTabInEditorAction} based on the provided action event.
   *
   * @param event         The action event triggering the update. Must not be null.
   * @param toolWindow    The tool window to be updated. Must not be null.
   * @param tabEditorFile The tab editor file associated with the tool window, or null if there is no such file.
   */
  void updatePresentation(@NotNull AnActionEvent event,
                          @NotNull ToolWindow toolWindow,
                          @Nullable ToolWindowTabFile tabEditorFile);

  /**
   * Performs the specified action when triggered within the given context.
   *
   * @param event         The action event that triggered this method. Must not be null.
   * @param toolWindow    The tool window associated with the action. Must not be null.
   * @param tabEditorFile The tab editor file associated with the tool window, or null if there is no such file.
   */
  default void performAction(@NotNull AnActionEvent event,
                             @NotNull ToolWindow toolWindow,
                             @Nullable ToolWindowTabFile tabEditorFile) { }
}