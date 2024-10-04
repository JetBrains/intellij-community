// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.vcs.changes.ChangesUtil.processVirtualFilesByVcs;
import static com.intellij.ui.SimpleTextAttributes.*;
import static com.intellij.util.FontUtil.spaceAndThinSpace;

@ApiStatus.Internal
public class ChangesBrowserLockedFoldersNode extends ChangesBrowserNode<ChangesBrowserNode.Tag> implements TreeLinkMouseListener.HaveTooltip {

  @NotNull private static final SimpleTextAttributes CLEANUP_LINK_ATTRIBUTES = new SimpleTextAttributes(STYLE_UNDERLINE, JBColor.RED);

  @NotNull private final Project myProject;

  public ChangesBrowserLockedFoldersNode(@NotNull Project project) {
    super(LOCKED_FOLDERS_TAG);
    myProject = project;
  }

  @Override
  @NotNull
  public String getTooltip() {
    return VcsBundle.message("changes.nodetitle.locked.folders.tooltip");
  }

  @Override
  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    renderer.append(LOCKED_FOLDERS_TAG.toString(), REGULAR_ATTRIBUTES);
    renderer.append(getCountText(), GRAY_ITALIC_ATTRIBUTES);
    renderer.append(spaceAndThinSpace(), REGULAR_ATTRIBUTES);
    renderer.append(VcsBundle.message("changes.do.cleanup"), CLEANUP_LINK_ATTRIBUTES, new CleanupWorker(myProject, this));
  }

  @Override
  public @Nls String getTextPresentation() {
    return LOCKED_FOLDERS_TAG.toString();
  }

  private static final class CleanupWorker implements Runnable {
    @NotNull private final Project myProject;
    @NotNull private final ChangesBrowserNode<?> myNode;

    private CleanupWorker(@NotNull Project project, @NotNull ChangesBrowserNode<?> node) {
      myProject = project;
      myNode = node;
    }

    @Override
    public void run() {
      processVirtualFilesByVcs(myProject, myNode.iterateFilesUnder().toList(), (vcs, files) -> {
        ChangeProvider provider = vcs.getChangeProvider();
        if (provider != null) {
          provider.doCleanup(files);
        }
      });
    }
  }
}
