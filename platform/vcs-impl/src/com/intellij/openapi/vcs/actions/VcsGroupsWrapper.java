/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.*;

public class VcsGroupsWrapper extends DefaultActionGroup implements DumbAware {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.actions.DefaultActionGroup");

  private final PresentationFactory myPresentationFactory = new PresentationFactory();
  private AnAction[] myChildren;

  public void update(AnActionEvent e) {
    VcsContext dataContext = VcsContextWrapper.createInstanceOn(e);
    if (myChildren == null) {
      DefaultActionGroup vcsGroupsGroup = (DefaultActionGroup)ActionManager.getInstance().getAction("VcsGroup");
      ArrayList<AnAction> validChildren = new ArrayList<AnAction>();
      AnAction[] children = vcsGroupsGroup.getChildren(new AnActionEvent(null, e.getDataContext(), e.getPlace(), myPresentationFactory.getPresentation(
        vcsGroupsGroup),
                                                                         ActionManager.getInstance(),
                                                                         0));
      for (AnAction child : children) {
        if (!(child instanceof StandardVcsGroup)) {
          LOG.error("Any version control group should extends com.intellij.openapi.vcs.actions.StandardVcsGroup class. Groupd class: " +
                    child.getClass().getName() + ", group ID: " + ActionManager.getInstance().getId(child));
        }
        else {
          validChildren.add(child);
        }
      }

      myChildren = validChildren.toArray(new AnAction[validChildren.size()]);

    }

    Project project = dataContext.getProject();
    Presentation presentation = e.getPresentation();
    if (project == null) {
      presentation.setVisible(false);
      return;
    }

    Collection<String> currentVcses = new HashSet<String>();

    VirtualFile[] selectedFiles = dataContext.getSelectedFiles();

    ProjectLevelVcsManager projectLevelVcsManager = ProjectLevelVcsManager.getInstance(project);

    Map<String, AnAction> vcsToActionMap = new HashMap<String, AnAction>();

    for (AnAction aMyChildren : myChildren) {
      StandardVcsGroup child = (StandardVcsGroup)aMyChildren;
      String vcsName = child.getVcsName(project);
      vcsToActionMap.put(vcsName, child);
    }

    if (selectedFiles != null) {
      for (VirtualFile selectedFile : selectedFiles) {
        AbstractVcs vcs = projectLevelVcsManager.getVcsFor(selectedFile);
        if (vcs != null) {
          currentVcses.add(vcs.getName());
        }
      }
    }


    if (currentVcses.size() == 1 && vcsToActionMap.containsKey(currentVcses.iterator().next())) {
      updateFromAction(vcsToActionMap.get(currentVcses.iterator().next()), presentation);
    }
    else {
      DefaultActionGroup composite = new DefaultActionGroup(VcsBundle.message("group.name.version.control"), true);
      for (AnAction aMyChildren : myChildren) {
        StandardVcsGroup child = (StandardVcsGroup)aMyChildren;
        String vcsName = child.getVcsName(project);
        if (currentVcses.contains(vcsName)) {
          composite.add(child);
        }
      }
      updateFromAction(composite, presentation);

      if (currentVcses.size() == 0) e.getPresentation().setVisible(false);
    }

    super.update(e);
  }

  private void updateFromAction(AnAction action, Presentation presentation) {
    Presentation wrappedActionPresentation = myPresentationFactory.getPresentation(action);
    presentation.setDescription(wrappedActionPresentation.getDescription());
    presentation.restoreTextWithMnemonic(wrappedActionPresentation);
    presentation.setVisible(wrappedActionPresentation.isVisible());
    presentation.setEnabled(wrappedActionPresentation.isEnabled());
    removeAll();
    DefaultActionGroup wrappedGroup = (DefaultActionGroup)action;
    for (AnAction aChildren : wrappedGroup.getChildren(null)) {
      add(aChildren);
    }

  }

}
