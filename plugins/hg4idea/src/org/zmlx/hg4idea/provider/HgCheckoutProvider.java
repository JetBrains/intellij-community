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
package org.zmlx.hg4idea.provider;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgCloneCommand;
import org.zmlx.hg4idea.command.HgCommandResult;
import org.zmlx.hg4idea.ui.HgCloneDialog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;

/**
 * Checkout provider for Mercurial
 */
public class HgCheckoutProvider implements CheckoutProvider {

  private static final Logger LOG = Logger.getInstance(HgCheckoutProvider.class.getName());

  public void doCheckout(@NotNull final Project project, @Nullable final Listener listener) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });

    final HgCloneDialog dialog = new HgCloneDialog(project);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    final VirtualFile destinationParent = LocalFileSystem.getInstance().findFileByIoFile(new File(dialog.getParentDirectory()));
    if (destinationParent == null) {
      return;
    }
    final String targetDir = destinationParent.getPath() + File.separator + dialog.getDirectoryName();

    final String sourceRepositoryURL = dialog.getSourceRepositoryURL();
    new Task.Backgroundable(project, HgVcsMessages.message("hg4idea.clone.progress", sourceRepositoryURL), true) {
      @Override public void run(@NotNull ProgressIndicator indicator) {
        // clone
        HgCloneCommand clone = new HgCloneCommand(project);
        clone.setRepositoryURL(sourceRepositoryURL);
        clone.setDirectory(targetDir);

        // handle result
        try {
          final HgCommandResult myCloneResult = clone.execute();
          if (myCloneResult == null) {
            notifyError("Clone failed", "Clone failed due to unknown error", project);
          } else if (myCloneResult.getExitValue() != 0) {
            notifyError("Clone failed", "Clone from " + sourceRepositoryURL + " failed.<br/><br/>" + myCloneResult.getRawError(), project);
          } else {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                if (listener != null) {
                  listener.directoryCheckedOut(new File(dialog.getParentDirectory(), dialog.getDirectoryName()));
                  listener.checkoutCompleted();
                }
              }
            });
          }
        } finally {
          cleanupAuthDataFromHgrc(targetDir);
        }
      }
    }.queue();

  }

  /**
   * Removes authentication data from the parent URL of a just cloned repository.
   * @param targetDir directory where the hg project was cloned into.
   */
  private static void cleanupAuthDataFromHgrc(String targetDir) {
    File hgrc = new File(new File(targetDir, ".hg"), "hgrc");
    if (!hgrc.exists()) {
      return;
    }

    for (int i = 0; i < 3; i++) { // 3 attempts in case of an IOException
      BufferedReader reader = null;
      PrintWriter writer = null;
      try {
        // writing correct info into a temporary file
        final File tempFile = FileUtil.createTempFile("hgrc", "temp");
        tempFile.deleteOnExit();
        reader = new BufferedReader(new FileReader(hgrc));
        writer = new PrintWriter(new FileWriter(tempFile));
        String line;
        while ((line = reader.readLine()) != null) {
          String parseLine = line.trim();
          if (parseLine.startsWith("default") && parseLine.contains("@")) { // looking for paths.default
            int eqIdx = parseLine.indexOf('=');
            parseLine = parseLine.substring(eqIdx+1).trim();  // getting value of paths.default
            try {
              final URI uri = new URI(parseLine);
              final String urlWithoutAuthData = uri.toString().replace(uri.getUserInfo() + "@", "");
              writer.println("default = " + urlWithoutAuthData);
            } catch (Throwable t) { // not URI => no sensitive data
              writer.println(line);
            }
          } else {
            writer.println(line);
          }
        }

        // substituting files
        if (!tempFile.renameTo(hgrc)) { // this may fail in case of different FSs
          FileUtil.copy(tempFile, hgrc);
          tempFile.delete();
        }
        return;
      } catch (IOException e) {
        LOG.info(e);
      } finally {
        if (reader != null) {
          try {
            reader.close();
          } catch (IOException e) {
            continue;
          }
        }
        if (writer != null) {
          writer.close();
        }
      }
    }
  }

  private static void notifyError(String title, String description, Project project) {
    Notifications.Bus.notify(new Notification(HgVcs.NOTIFICATION_GROUP_ID, title, description, NotificationType.ERROR), project);
  }

  /**
   * {@inheritDoc}
   */
  public String getVcsName() {
    return "_Mercurial";
  }
}
