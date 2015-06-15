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

import com.intellij.icons.AllIcons;
import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.labels.ActionLink;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

/**
 * @author Konstantin Bulenkov
 */
public class ChangeProjectIconForm {
  private final String myProjectPath;
  JBLabel myDefaultIcon;
  JBLabel myDarculaIcon;
  JPanel myRootPanel;
  ActionLink mySetIcon;
  ActionLink mySetIconDark;
  ActionLink myClear;
  ActionLink myClearDark;
  boolean resetIcon;
  boolean resetDarkIcon;
  VirtualFile pathToIcon;
  VirtualFile pathToDarkIcon;

  public ChangeProjectIconForm(String projectPath) {
    myProjectPath = projectPath;
    myClear.setEnabled(getIcon() != null);
    myClearDark.setEnabled(getDarculaIcon() != null);
    Icon projectOrAppIcon = RecentProjectsManagerBase.getProjectOrAppIcon(myProjectPath);
    Icon defaultIcon = RecentProjectsManagerBase.getProjectIcon(myProjectPath, false);
    myDefaultIcon.setIcon(defaultIcon == null ? projectOrAppIcon : defaultIcon);
    Icon darculaIcon = RecentProjectsManagerBase.getProjectIcon(myProjectPath, true);
    myDarculaIcon.setIcon(darculaIcon == null ? myDefaultIcon.getIcon() : darculaIcon);
  }

  Icon getIcon() {
    return RecentProjectsManagerBase.getProjectIcon(myProjectPath, false);
  }

  Icon getDarculaIcon() {
    return RecentProjectsManagerBase.getProjectIcon(myProjectPath, true);
  }

  private void createUIComponents() {
    mySetIcon = new ActionLink("Change...", new ChangeProjectIcon(false));
    mySetIconDark = new ActionLink("Change...", new ChangeProjectIcon(true));
    myClear = new ActionLink("Reset", new ResetProjectIcon(false));
    myClearDark = new ActionLink("Reset", new ResetProjectIcon(true));
  }

  public void apply() throws IOException {
    File darkIcon = new File(myProjectPath + "/.idea/icon_dark.png");
    File icon = new File(myProjectPath + "/.idea/icon.png");

    if (pathToDarkIcon != null) {
      FileUtil.copy(new File(pathToDarkIcon.getPath()), darkIcon);
    }
    else if (resetDarkIcon) {
      FileUtil.delete(darkIcon);
    }
    if (pathToIcon != null) {
      FileUtil.copy(new File(pathToIcon.getPath()), icon);
    }
    else if (resetIcon) {
      FileUtil.delete(icon);
    }
  }

  private class ChangeProjectIcon extends AnAction {
    private final boolean myDarcula;

    public ChangeProjectIcon(boolean darcula) {
      myDarcula = darcula;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      VirtualFile[] files = FileChooserFactory.getInstance()
        .createFileChooser(new FileChooserDescriptor(true, false, false, false, false, false).withFileFilter(
          new Condition<VirtualFile>() {
            @Override
            public boolean value(VirtualFile file) {
              return "png".equalsIgnoreCase(file.getExtension());
            }
          }), null, null).choose(null);

      if (files.length == 1) {
        try {
          Icon newIcon = RecentProjectsManagerBase.createIcon(new File(files[0].getPath()));
          if (myDarcula) {
            myDarculaIcon.setIcon(newIcon);
            pathToDarkIcon = files[0];
          }
          else {
            myDefaultIcon.setIcon(newIcon);
            pathToIcon = files[0];
          }
        }
        catch (Exception e1) {
        }
      }
    }
  }

  private class ResetProjectIcon extends AnAction {
    private final boolean myDarcula;

    public ResetProjectIcon(boolean darcula) {
      myDarcula = darcula;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (myDarcula) {
        myDarculaIcon.setIcon(AllIcons.Nodes.IdeaProject);
        resetDarkIcon = true;
      }
      else {
        myDefaultIcon.setIcon(AllIcons.Nodes.IdeaProject);
        resetIcon = true;
      }
    }
  }
}
