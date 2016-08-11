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
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeListOwner;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.util.FontUtil.spaceAndThinSpace;

public class ChangesBrowserLockedFoldersNode extends ChangesBrowserNode implements TreeLinkMouseListener.HaveTooltip {
  private final Project myProject;

  public ChangesBrowserLockedFoldersNode(final Project project, final Object userObject) {
    super(userObject);
    myProject = project;
  }

  public boolean canAcceptDrop(final ChangeListDragBean dragBean) {
    return false;
  }

  public void acceptDrop(final ChangeListOwner dragOwner, final ChangeListDragBean dragBean) {
  }

  public String getTooltip() {
    return VcsBundle.message("changes.nodetitle.locked.folders.tooltip");
  }

  public void render(final ChangesBrowserNodeRenderer renderer, final boolean selected, final boolean expanded, final boolean hasFocus) {
    renderer.append(userObject.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    renderer.append(getCountText(), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
    renderer.append(spaceAndThinSpace(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    final CleanupStarter starter = new CleanupStarter(myProject, this);
    renderer.append("do cleanup...", new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, JBColor.RED), starter);
  }

  private static class CleanupStarter implements Runnable {
    private final Project myProject;
    private final ChangesBrowserLockedFoldersNode myParentNode;

    private CleanupStarter(final Project project, final ChangesBrowserLockedFoldersNode parentNode) {
      myProject = project;
      myParentNode = parentNode;
    }

    public void run() {
      final List<VirtualFile> files = myParentNode.getAllFilesUnder();
      final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstance(myProject);
      final Map<String, List<VirtualFile>> byVcs = new HashMap<>();
      for (VirtualFile file : files) {
        final AbstractVcs vcs = plVcsManager.getVcsFor(file);
        if (vcs != null) {
          List<VirtualFile> list = byVcs.get(vcs.getName());
          if (list == null) {
            list = new ArrayList<>();
            byVcs.put(vcs.getName(), list);
          }
          list.add(file);
        }
      }
      for (Map.Entry<String, List<VirtualFile>> entry : byVcs.entrySet()) {
        final AbstractVcs vcs = plVcsManager.findVcsByName(entry.getKey());
        if (vcs != null) {
          final ChangeProvider changeProvider = vcs.getChangeProvider();
          if (changeProvider != null) {
            changeProvider.doCleanup(entry.getValue());
          }
        }
      }
    }
  }
}
