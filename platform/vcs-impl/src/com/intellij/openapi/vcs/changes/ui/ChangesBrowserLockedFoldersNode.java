/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.vcs.changes.ChangesUtil.processVirtualFilesByVcs;
import static com.intellij.ui.SimpleTextAttributes.*;
import static com.intellij.util.FontUtil.spaceAndThinSpace;

public class ChangesBrowserLockedFoldersNode extends ChangesBrowserNode implements TreeLinkMouseListener.HaveTooltip {

  @NotNull private static final SimpleTextAttributes CLEANUP_LINK_ATTRIBUTES = new SimpleTextAttributes(STYLE_UNDERLINE, JBColor.RED);

  @NotNull private final Project myProject;

  public ChangesBrowserLockedFoldersNode(@NotNull Project project, @NotNull Object userObject) {
    super(userObject);
    myProject = project;
  }

  @NotNull
  public String getTooltip() {
    return VcsBundle.message("changes.nodetitle.locked.folders.tooltip");
  }

  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    renderer.append(userObject.toString(), REGULAR_ATTRIBUTES);
    renderer.append(getCountText(), GRAY_ITALIC_ATTRIBUTES);
    renderer.append(spaceAndThinSpace(), REGULAR_ATTRIBUTES);
    renderer.append("do cleanup...", CLEANUP_LINK_ATTRIBUTES, new CleanupWorker(myProject, this));
  }

  private static class CleanupWorker implements Runnable {
    @NotNull private final Project myProject;
    @NotNull private final ChangesBrowserNode<?> myNode;

    private CleanupWorker(@NotNull Project project, @NotNull ChangesBrowserNode<?> node) {
      myProject = project;
      myNode = node;
    }

    public void run() {
      processVirtualFilesByVcs(myProject, myNode.getAllFilesUnder(), (vcs, files) -> {
        ChangeProvider provider = vcs.getChangeProvider();
        if (provider != null) {
          provider.doCleanup(files);
        }
      });
    }
  }
}
