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
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.ProjectGroupActionGroup;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;

import java.awt.event.InputEvent;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class OpenSelectedProjectsAction extends RecentProjectsWelcomeScreenActionBase {
  @Override
  public void actionPerformed(AnActionEvent e) {
    List<AnAction> elements = getSelectedElements(e);
    e = new AnActionEvent(e.getInputEvent(), e.getDataContext(), e.getPlace(), e.getPresentation(), e.getActionManager(), InputEvent.SHIFT_MASK);
    for (AnAction element : elements) {
      if (element instanceof ProjectGroupActionGroup) {
        for (AnAction action : ((ProjectGroupActionGroup)element).getChildren(e)) {
          action.actionPerformed(e);
        }
      } else {
        element.actionPerformed(e);
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    List<AnAction> selectedElements = getSelectedElements(e);
    boolean hasProject = false;
    boolean hasGroup = false;
    for (AnAction element : selectedElements) {
      if (element instanceof ReopenProjectAction) {
        hasProject = true;
      }
      if (element instanceof ProjectGroupActionGroup) {
        hasGroup = true;
      }

      if (hasGroup && hasProject) {
        e.getPresentation().setEnabled(false);
        return;
      }
    }
    if (ActionPlaces.WELCOME_SCREEN.equals(e.getPlace())) {
      presentation.setEnabledAndVisible(true);
      if (selectedElements.size() == 1 && selectedElements.get(0) instanceof ProjectGroupActionGroup) {
        presentation.setText("Open All Projects in Group");
      } else {
        presentation.setText("Open Selected");
      }
    } else {
      presentation.setEnabledAndVisible(false);
    }
  }
}
