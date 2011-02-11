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
package git4idea.convert;

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import git4idea.config.GitVcsSettings;
import git4idea.ui.GitConvertFilesDialog;

import java.io.IOException;
import java.util.*;

/**
 * @author Kirill Likhodedov
 */
public class GitFileSeparatorConverter {

  /**
   * Check if files need to be converted to other line separator.
   * The method could be invoked from non-UI thread.
   *
   * @param project       the project to use
   * @param settings      the vcs settings
   * @param sortedChanges changes sorted by vcs roots.
   * @param exceptions    the collection with exceptions
   * @return true if conversion completed successfully, false if process was cancelled or there were errors
   */
  public static boolean convertSeparatorsIfNeeded(final Project project,
                                                  final GitVcsSettings settings,
                                                  Map<VirtualFile, Collection<Change>> sortedChanges,
                                                  final List<VcsException> exceptions) {
    final GitVcsSettings.ConversionPolicy conversionPolicy = settings.getLineSeparatorsConversion();
    if (conversionPolicy != GitVcsSettings.ConversionPolicy.NONE) {
      LocalFileSystem lfs = LocalFileSystem.getInstance();
      final String nl = CodeStyleFacade.getInstance(project).getLineSeparator();
      final Map<VirtualFile, Set<VirtualFile>> files = new HashMap<VirtualFile, Set<VirtualFile>>();
      // preliminary screening of files
      for (Map.Entry<VirtualFile, Collection<Change>> entry : sortedChanges.entrySet()) {
        final VirtualFile root = entry.getKey();
        final Set<VirtualFile> added = new HashSet<VirtualFile>();
        for (Change change : entry.getValue()) {
          switch (change.getType()) {
            case NEW:
            case MODIFICATION:
            case MOVED:
              VirtualFile f = lfs.findFileByPath(change.getAfterRevision().getFile().getPath());
              if (f != null && !f.getFileType().isBinary() && !nl.equals(LoadTextUtil.detectLineSeparator(f, false))) {
                added.add(f);
              }
              break;
            case DELETED:
          }
        }
        if (!added.isEmpty()) {
          files.put(root, added);
        }
      }
      // check crlf for real
      for (Iterator<Map.Entry<VirtualFile, Set<VirtualFile>>> i = files.entrySet().iterator(); i.hasNext();) {
        Map.Entry<VirtualFile, Set<VirtualFile>> e = i.next();
        Set<VirtualFile> fs = e.getValue();
        for (Iterator<VirtualFile> j = fs.iterator(); j.hasNext();) {
          VirtualFile f = j.next();
          String detectedLineSeparator = LoadTextUtil.detectLineSeparator(f, true);
          if (detectedLineSeparator == null || nl.equals(detectedLineSeparator)) {
            j.remove();
          }
        }
        if (fs.isEmpty()) {
          i.remove();
        }
      }
      if (files.isEmpty()) {
        return true;
      }
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        public void run() {
          VirtualFile[] selectedFiles = null;
          if (settings.getLineSeparatorsConversion() == GitVcsSettings.ConversionPolicy.ASK) {
            GitConvertFilesDialog d = new GitConvertFilesDialog(project, files);
            d.show();
            if (d.isOK()) {
              if (d.isDontShowAgainChosen()) {
                settings.setLineSeparatorsConversion(GitVcsSettings.ConversionPolicy.CONVERT);
              }
              selectedFiles = d.getSelectedFiles();
            } else if (d.getExitCode() == GitConvertFilesDialog.DO_NOT_CONVERT) {
              if (d.isDontShowAgainChosen()) {
                settings.setLineSeparatorsConversion(GitVcsSettings.ConversionPolicy.NONE);
              }
            } else {
              //noinspection ThrowableInstanceNeverThrown
              exceptions.add(new VcsException("Commit was cancelled in file conversion dialog"));
            }
          } else {
            ArrayList<VirtualFile> fileList = new ArrayList<VirtualFile>();
            for (Set<VirtualFile> fileSet : files.values()) {
              fileList.addAll(fileSet);
            }
            selectedFiles = VfsUtil.toVirtualFileArray(fileList);
          }
          if (selectedFiles != null) {
            for (VirtualFile f : selectedFiles) {
              if (f == null) {
                continue;
              }
              try {
                LoadTextUtil.changeLineSeparator(project, GitConvertFilesDialog.class.getName(), f, nl);
              } catch (IOException e) {
                //noinspection ThrowableInstanceNeverThrown
                exceptions.add(new VcsException("Failed to change line separators for the file: " + f.getPresentableUrl(), e));
              }
            }
          }
        }
      });
    }
    return exceptions.isEmpty();
  }

}
