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
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.FileContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ChangeDiffRequestPresentable implements DiffRequestPresentable {
  private final Project myProject;
  private final Change myChange;

  public ChangeDiffRequestPresentable(final Project project, final Change change) {
    myChange = change;
    myProject = project;
  }

  public MyResult step(DiffChainContext context) {
    final SimpleDiffRequest request = new SimpleDiffRequest(myProject, null);
    return new MyResult(request, getRequestForChange(request, context));
  }

  @Nullable
  private DiffPresentationReturnValue getRequestForChange(final SimpleDiffRequest request, final DiffChainContext context) {
    if (! canShowChange(context)) return DiffPresentationReturnValue.removeFromList;
    if (! loadCurrentContents(request, myChange)) return DiffPresentationReturnValue.quit;
    return DiffPresentationReturnValue.useRequest;
  }

  public boolean haveStuff() {
    return checkContentsAvailable(myChange.getBeforeRevision(), myChange.getAfterRevision());
  }

  public List<? extends AnAction> createActions(ShowDiffAction.DiffExtendUIFactory uiFactory) {
    return uiFactory.createActions(myChange);
  }

  private boolean loadCurrentContents(final SimpleDiffRequest request, final Change change) {
    final ContentRevision bRev = change.getBeforeRevision();
    final ContentRevision aRev = change.getAfterRevision();

    String beforePath = bRev != null ? bRev.getFile().getPath() : null;
    String afterPath = aRev != null ? aRev.getFile().getPath() : null;
    String title;
    if (beforePath != null && afterPath != null && !beforePath.equals(afterPath)) {
      title = beforePath + " -> " + afterPath;
    }
    else if (beforePath != null) {
      title = beforePath;
    }
    else if (afterPath != null) {
      title = afterPath;
    }
    else {
      title = VcsBundle.message("diff.unknown.path.title");
    }
    request.setWindowTitle(title);

    boolean result = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        request.setContents(createContent(bRev), createContent(aRev));
      }
    }, VcsBundle.message("progress.loading.diff.revisions"), true, myProject);
    if (! result) return false;

    String beforeRevisionTitle = (bRev != null) ? bRev.getRevisionNumber().asString() : "";
    String afterRevisionTitle = (aRev != null) ? aRev.getRevisionNumber().asString() : "";
    if ((beforeRevisionTitle == null) || (beforeRevisionTitle.length() == 0)) {
      beforeRevisionTitle = "Base version";
    }
    if ((afterRevisionTitle == null) || (afterRevisionTitle.length() == 0)) {
      afterRevisionTitle = "Your version";
    }
    request.setContentTitles(beforeRevisionTitle, afterRevisionTitle);
    return true;
  }

  @NotNull
  private DiffContent createContent(final ContentRevision revision) {
    ProgressManager.checkCanceled();
    if (revision == null) return new SimpleContent("");
    if (revision instanceof CurrentContentRevision) {
      final CurrentContentRevision current = (CurrentContentRevision)revision;
      final VirtualFile vFile = current.getVirtualFile();
      return vFile != null ? new FileContent(myProject, vFile) : new SimpleContent("");
    }

    String revisionContent;
    try {
      revisionContent = revision.getContent();
    }
    catch(VcsException ex) {
      // TODO: correct exception handling
      revisionContent = null;
    }
    SimpleContent content = revisionContent == null
                            ? new SimpleContent("")
                            : new SimpleContent(revisionContent, revision.getFile().getFileType());
    VirtualFile vFile = revision.getFile().getVirtualFile();
    if (vFile != null) {
      content.setCharset(vFile.getCharset());
      content.setBOM(vFile.getBOM());
    }
    content.setReadOnly(true);
    return content;
  }

  private boolean canShowChange(DiffChainContext context) {
    final ContentRevision bRev = myChange.getBeforeRevision();
    final ContentRevision aRev = myChange.getAfterRevision();

    if ((bRev != null && (bRev.getFile().getFileType().isBinary() || bRev.getFile().isDirectory())) ||
        (aRev != null && (aRev.getFile().getFileType().isBinary() || aRev.getFile().isDirectory()))) {
      if (bRev != null && bRev.getFile().getFileType() == FileTypes.UNKNOWN && !bRev.getFile().isDirectory()) {
        if (! checkContentsAvailable(bRev, aRev)) return false;
        if (!checkAssociate(myProject, bRev.getFile(), context)) return false;
      }
      else if (aRev != null && aRev.getFile().getFileType() == FileTypes.UNKNOWN && !aRev.getFile().isDirectory()) {
        if (! checkContentsAvailable(bRev, aRev)) return false;
        if (!checkAssociate(myProject, aRev.getFile(), context)) return false;
      }
      else {
        return false;
      }
    }
    return true;
  }

  private static boolean hasContents(@Nullable final ContentRevision rev) {
    if (rev == null) return false;
      try {
        if (rev instanceof BinaryContentRevision) {
        return ((BinaryContentRevision) rev).getBinaryContent() != null;
        } else {
          return rev.getContent() != null;
        }
      }
      catch (VcsException e) {
        return false;
      }
  }

  private static boolean checkContentsAvailable(@Nullable final ContentRevision bRev, @Nullable final ContentRevision aRev) {
    if (hasContents(bRev)) return true;
    return hasContents(aRev);
  }

  private static boolean checkAssociate(final Project project, final FilePath file, DiffChainContext context) {
    final String pattern = FileUtil.getExtension(file.getName());
    if (context.contains(pattern)) return false;
    int rc = Messages.showDialog(project,
                                 VcsBundle.message("diff.unknown.file.type.prompt", file.getName()),
                                 VcsBundle.message("diff.unknown.file.type.title"),
                                 new String[] {
                                   VcsBundle.message("diff.unknown.file.type.associate"),
                                   CommonBundle.getCancelButtonText()
                                 }, 0, Messages.getQuestionIcon());
    if (rc == 0) {
      FileType fileType = FileTypeChooser.associateFileType(file.getName());
      return fileType != null && !fileType.isBinary();
    } else {
      context.add(pattern);
    }
    return false;
  }
}
