/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.util.SingleAlarm;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.TOOLWINDOW_ID;

public abstract class DnDActivateOnHoldTargetContent extends ContentImpl implements DnDTarget {
  private final SingleAlarm myAlarm;
  @NotNull private final Project myProject;

  protected DnDActivateOnHoldTargetContent(@NotNull Project project, JComponent component, String displayName, boolean isLockable) {
    super(component, displayName, isLockable);
    myProject = project;
    myAlarm = new SingleAlarm(() -> activateContent(), 700);
  }

  @Override
  public boolean update(DnDEvent event) {
    boolean isDropPossible = isDropPossible(event);
    event.setDropPossible(isDropPossible);
    if (isDropPossible) {
      if (myAlarm.isEmpty()) {
        myAlarm.request();
      }
    }
    else {
      myAlarm.cancelAllRequests();
    }
    return !isDropPossible;
  }

  @Override
  public void cleanUpOnLeave() {
    myAlarm.cancelAllRequests();
  }

  @Override
  public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
  }

  private void activateContent() {
    ChangesViewContentManager.getInstance(myProject).setSelectedContent(this);
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TOOLWINDOW_ID);
    if (toolWindow!=null && !toolWindow.isVisible()) {
      toolWindow.activate(null);
    }
  }

  public abstract boolean isDropPossible(@NotNull DnDEvent event);

  @Override
  public void drop(DnDEvent event) {
    myAlarm.cancelAllRequests();
  }
}