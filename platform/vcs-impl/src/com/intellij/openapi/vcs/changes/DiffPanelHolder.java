/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.diff.ex.DiffPanelEx;
import com.intellij.openapi.diff.ex.DiffPanelOptions;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.project.Project;

import java.util.LinkedList;

/**
* @author irengrig
*         Date: 6/18/11
*         Time: 3:00 PM
*/
public class DiffPanelHolder {
  public static final int cacheLimit = 10;
  private final LinkedList<DiffPanel> myCache;
  private final LinkedList<DiffPanel> myOwnList;
  protected final Project myProject;

  public DiffPanelHolder(LinkedList<DiffPanel> cache, Project project) {
    myCache = cache;
    myProject = project;
    myOwnList = new LinkedList<DiffPanel>();
  }

  public void resetPanels() {
    for (DiffPanel diffPanel : myOwnList) {
      ((DiffPanelImpl) diffPanel).reset();
    }
    myCache.addAll(myOwnList);
    while (myCache.size() > cacheLimit) {
      myCache.removeFirst();
    }
    myOwnList.clear();
  }

  public DiffPanel getOrCreate() {
    if (myCache.isEmpty()) {
      final DiffPanel diffPanel = create();
      myOwnList.addLast(diffPanel);
      return diffPanel;
    }
    final DiffPanel diffPanel = myCache.removeFirst();
    myOwnList.addLast(diffPanel);
    return diffPanel;
  }

  protected DiffPanel create() {
    final DiffPanel diffPanel = DiffManager.getInstance().createDiffPanel(null, myProject);
    diffPanel.enableToolbar(false);
    diffPanel.removeStatusBar();
    DiffPanelOptions o = ((DiffPanelEx)diffPanel).getOptions();
    o.setRequestFocusOnNewContent(false);
    return diffPanel;
  }
}
