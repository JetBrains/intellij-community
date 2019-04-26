// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.icons.AllIcons;
import com.intellij.ide.RecentProjectIconHelper;
import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.labels.ActionLink;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

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

  public ChangeProjectIconForm(@NotNull String projectPath) {
    myProjectPath = projectPath;
    myClear.setEnabled(getIcon() != null);
    myClearDark.setEnabled(getDarculaIcon() != null);
    RecentProjectsManagerBase recentProjectsManager = RecentProjectsManagerBase.getInstanceEx();
    Icon projectOrAppIcon = recentProjectsManager.getProjectOrAppIcon(myProjectPath);
    Icon defaultIcon = recentProjectsManager.getProjectIcon(myProjectPath, false);
    myDefaultIcon.setIcon(defaultIcon == null ? projectOrAppIcon : defaultIcon);
    Icon darculaIcon = recentProjectsManager.getProjectIcon(myProjectPath, true);
    myDarculaIcon.setIcon(darculaIcon == null ? myDefaultIcon.getIcon() : darculaIcon);
  }

  Icon getIcon() {
    return RecentProjectsManagerBase.getInstanceEx().getProjectIcon(myProjectPath, false);
  }

  Icon getDarculaIcon() {
    return RecentProjectsManagerBase.getInstanceEx().getProjectIcon(myProjectPath, true);
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

    ChangeProjectIcon(boolean darcula) {
      myDarcula = darcula;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      VirtualFile[] files = FileChooserFactory.getInstance()
        .createFileChooser(new FileChooserDescriptor(true, false, false, false, false, false).withFileFilter(
          file -> "png".equalsIgnoreCase(file.getExtension())), null, null).choose(null);

      if (files.length == 1) {
        try {
          Icon newIcon = RecentProjectIconHelper.createIcon(Paths.get(files[0].getPath()));
          if (myDarcula) {
            myDarculaIcon.setIcon(newIcon);
            pathToDarkIcon = files[0];
          }
          else {
            myDefaultIcon.setIcon(newIcon);
            pathToIcon = files[0];
          }
        }
        catch (Exception ignore) {
        }
      }
    }
  }

  private class ResetProjectIcon extends AnAction {
    private final boolean myDarcula;

    ResetProjectIcon(boolean darcula) {
      myDarcula = darcula;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
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
