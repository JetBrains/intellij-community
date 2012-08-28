/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.zmlx.hg4idea.util;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.hash.HashSet;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;

import javax.swing.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class HgCustomUtil {
  public static void showSuccessNotification(Project project, String message) {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    JComponent vcsComponent = HgVcs.getInstance(project).getCurrentBranchStatus().getComponent();
    JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, MessageType.INFO, null).setFadeoutTime(5000).createBalloon()
      .show(RelativePoint.getCenterOf(vcsComponent), Balloon.Position.atRight);
  }


  public static String getCurrentBranch(Project project, VirtualFile repository) {
    HgCommandResult result = new HgCommandExecutor(project).executeInCurrentThread(repository, "branch", null);
    return result.getOutputLines().get(0);
  }

  public static List<String> getBranchNames(Project project, VirtualFile repository) {
    List<String> args = new LinkedList<String>(); args.add("-c");
    HgCommandResult result = new HgCommandExecutor(project).executeInCurrentThread(repository, "branches", args);
    List<String> nameList = new LinkedList<String>();

    for (String output : result.getOutputLines()) {
      nameList.add(output.substring(0, output.indexOf(" ")));
    }

    return nameList;
  }

  public static List<String> getTagNames(Project project, VirtualFile repository) {
    HgCommandResult result = new HgCommandExecutor(project).executeInCurrentThread(repository, "tags", null);
    List<String> nameList = new LinkedList<String>();

    for (String output : result.getOutputLines()) {
      nameList.add(output.substring(0, output.indexOf(" ")));
    }

    return nameList;
  }

  public static HgCommandResult createBranch(Project project, VirtualFile repository, String branchName) {
    List<String> args = new LinkedList<String>();
    args.add(branchName);

    return new HgCommandExecutor(project).executeInCurrentThread(repository, "branch", args);
  }

  public static Set<VirtualFile> getRepositories(AnActionEvent e) {
    VirtualFile[] roots = (VirtualFile[])e.getDataContext().getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY.getName());
    Set<VirtualFile> uniqueRoots = new HashSet<VirtualFile>();

    for (VirtualFile root : roots) {
      VirtualFile repository = HgUtil.getHgRootOrNull(e.getProject(), root);
      if (repository != null) {
        uniqueRoots.add(repository);
      }
    }

    return uniqueRoots;
  }


  public static HgCommandResult closeBranch(Project project, VirtualFile repository, String branchName) {
    List<String> args = new LinkedList<String>();
    args.add("--close-branch");
    args.add("-m Closing a deprecated branch.");

    return new HgCommandExecutor(project).executeInCurrentThread(repository, "commit", args);
  }

  public static HgCommandResult updateToDefault(Project project, VirtualFile repository) {
    List<String> args = new LinkedList<String>();
    args.add("default");

    return new HgCommandExecutor(project).executeInCurrentThread(repository, "update", args);
  }
}
