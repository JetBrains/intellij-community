// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangesTreeCompatibilityProvider;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.ui.SimpleTextAttributes.*;
import static com.intellij.util.FontUtil.spaceAndThinSpace;

@ApiStatus.Internal
public class ChangesBrowserLockedFoldersNode extends ChangesBrowserNode<ChangesBrowserNode.Tag>
  implements TreeLinkMouseListener.HaveTooltip {

  private static final @NotNull SimpleTextAttributes CLEANUP_LINK_ATTRIBUTES = new SimpleTextAttributes(STYLE_UNDERLINE, JBColor.RED);

  private final @NotNull Project myProject;

  public ChangesBrowserLockedFoldersNode(@NotNull Project project) {
    super(LOCKED_FOLDERS_TAG);
    myProject = project;
  }

  @Override
  public @NotNull String getTooltip() {
    return VcsBundle.message("changes.nodetitle.locked.folders.tooltip");
  }

  @Override
  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    renderer.append(LOCKED_FOLDERS_TAG.toString(), REGULAR_ATTRIBUTES);
    renderer.append(getCountText(), GRAY_ITALIC_ATTRIBUTES);

    Runnable cleanupWorker = ChangesTreeCompatibilityProvider.getInstance().getLockedFilesCleanupWorker(myProject, this);
    if (cleanupWorker != null) {
      renderer.append(spaceAndThinSpace(), REGULAR_ATTRIBUTES);
      renderer.append(VcsBundle.message("changes.do.cleanup"), CLEANUP_LINK_ATTRIBUTES, cleanupWorker);
    }
  }

  @Override
  public @Nls String getTextPresentation() {
    return LOCKED_FOLDERS_TAG.toString();
  }
}
