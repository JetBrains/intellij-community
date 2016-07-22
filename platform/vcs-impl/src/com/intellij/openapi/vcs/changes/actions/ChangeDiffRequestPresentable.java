/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.diff.FileAwareSimpleContent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.FileContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChangeDiffRequestPresentable implements DiffRequestPresentable {
  private final Project myProject;
  private final Change myChange;
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.actions.ChangeDiffRequestPresentable");
  // I don't like that much
  private boolean myIgnoreDirectoryFlag;

  public ChangeDiffRequestPresentable(final Project project, final Change change) {
    myChange = change;
    myProject = project;
  }

  public Change getChange() {
    return myChange;
  }

  public void setIgnoreDirectoryFlag(boolean ignoreDirectoryFlag) {
    myIgnoreDirectoryFlag = ignoreDirectoryFlag;
  }

  public MyResult step(DiffChainContext context) {
    final SimpleDiffRequest request = new SimpleDiffRequest(myProject, null);
    final List<String> errSb = new ArrayList<>();
    if (! canShowChange(context, errSb)) {
      return new MyResult(request, DiffPresentationReturnValue.removeFromList, StringUtil.join(errSb, "\n"));
    }
    if (! loadCurrentContents(request, myChange)) return new MyResult(request, DiffPresentationReturnValue.quit);
    return new MyResult(request, DiffPresentationReturnValue.useRequest);
  }

  @Override
  public String getPathPresentation() {
    return ChangesUtil.getFilePath(myChange).getPath();
  }

  @Override
  public void haveStuff() throws VcsException {
    final List<String> errSb = new ArrayList<>();
    final boolean canShow = checkContentsAvailable(myChange.getBeforeRevision(), myChange.getAfterRevision(), errSb);
    if (! canShow) {
      throw new VcsException(StringUtil.join(errSb, "\n"));
    }
  }

  public List<? extends AnAction> createActions(DiffExtendUIFactory uiFactory) {
    return uiFactory.createActions(myChange);
  }

  private boolean loadCurrentContents(final SimpleDiffRequest request, final Change change) {
    final ContentRevision bRev = change.getBeforeRevision();
    final ContentRevision aRev = change.getAfterRevision();

    String beforePath = bRev != null ? bRev.getFile().getPath() : null;
    String afterPath = aRev != null ? aRev.getFile().getPath() : null;
    String title;
    if (beforePath != null && afterPath != null && !beforePath.equals(afterPath)) {
      beforePath = FileUtil.toSystemDependentName(beforePath);
      afterPath = FileUtil.toSystemDependentName(afterPath);
      title = beforePath + " -> " + afterPath;
    }
    else if (beforePath != null) {
      beforePath = FileUtil.toSystemDependentName(beforePath);
      title = beforePath;
    }
    else if (afterPath != null) {
      afterPath = FileUtil.toSystemDependentName(afterPath);
      title = afterPath;
    }
    else {
      title = VcsBundle.message("diff.unknown.path.title");
    }
    request.setWindowTitle(title);

    boolean result = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();
        if (pi != null) {
          pi.setIndeterminate(true);
        }
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
    if (revision == null) return SimpleContent.createEmpty();
    if (revision instanceof CurrentContentRevision) {
      final CurrentContentRevision current = (CurrentContentRevision)revision;
      final VirtualFile vFile = current.getVirtualFile();
      return vFile != null ? new FileContent(myProject, vFile) : new SimpleContent("");
    }
    FilePath filePath = revision.getFile();
    if (revision instanceof BinaryContentRevision) {
      final String name = filePath.getName();
      try {
        return FileContent.createFromTempFile(myProject, name, name, ((BinaryContentRevision)revision).getBinaryContent());
      }
      catch (IOException e) {
        LOG.info(e);
        try {
          return FileContent.createFromTempFile(myProject, name, name, ArrayUtil.EMPTY_BYTE_ARRAY);
        }
        catch (IOException e1) {
          LOG.info(e1);
          return null;
        }
      }
      catch (VcsException e) {
        LOG.info(e);
        try {
          return FileContent.createFromTempFile(myProject, name, name, ArrayUtil.EMPTY_BYTE_ARRAY);
        }
        catch (IOException e1) {
          LOG.info(e1);
          return null;
        }
      }
    }

    String revisionContent;
    try {
      revisionContent = revision.getContent();
    }
    catch(VcsException ex) {
      LOG.info(ex);
      // TODO: correct exception handling
      revisionContent = null;
    }
    SimpleContent content = revisionContent == null
                            ? new SimpleContent("")
                            : new FileAwareSimpleContent(myProject, filePath, revisionContent, filePath.getFileType());
    VirtualFile vFile = filePath.getVirtualFile();
    if (vFile != null) {
      content.setCharset(vFile.getCharset());
      content.setBOM(vFile.getBOM());
    }
    content.setReadOnly(true);
    return content;
  }

  private boolean canShowChange(DiffChainContext context, List<String> errSb) {
    final ContentRevision bRev = myChange.getBeforeRevision();
    final ContentRevision aRev = myChange.getAfterRevision();

    if (myIgnoreDirectoryFlag) {
      if (! checkContentsAvailable(bRev, aRev, errSb)) return false;
      return true;
    }

    boolean isOk = checkContentRevision(bRev, context, errSb);
    isOk &= checkContentRevision(aRev, context, errSb);

    return isOk;
  }

  private boolean checkContentRevision(ContentRevision rev, final DiffChainContext context, final List<String> errSb) {
    if (rev == null) return true;
    if (rev.getFile().isDirectory()) return false;
    if (! hasContents(rev, errSb)) {
      return false;
    }
    final FileType type = rev.getFile().getFileType();
    if (! type.isBinary()) return true;
    if (FileTypes.UNKNOWN.equals(type)) {
      final boolean associatedToText = checkAssociate(myProject, rev.getFile().getName(), context);
    }
    return true;
  }

  private static boolean hasContents(@Nullable final ContentRevision rev, final List<String> errSb) {
    if (rev == null) return false;
      try {
        if (rev instanceof BinaryContentRevision) {
        return ((BinaryContentRevision) rev).getBinaryContent() != null;
        } else {
          return rev.getContent() != null;
        }
      }
      catch (VcsException e) {
        LOG.info(e);
        errSb.add(e.getMessage());
        return false;
      }
  }

  private static boolean checkContentsAvailable(@Nullable final ContentRevision bRev, @Nullable final ContentRevision aRev, final List<String> errSb) {
    if (hasContents(bRev, errSb)) return true;
    if (hasContents(aRev, errSb)) return true;
    errSb.add("Can't load revisions content:");
    if (bRev != null) errSb.add("Can't load content of " + bRev.getFile().getPresentableUrl() + " at " + bRev.getRevisionNumber().asString());
    if (aRev != null) errSb.add("Can't load content of " + aRev.getFile().getPresentableUrl() + " at " + aRev.getRevisionNumber().asString());
    if (aRev == null && bRev == null) errSb.add("Both revisions are empty");
    return false;
  }

  public static boolean checkAssociate(final Project project, String fileName, DiffChainContext context) {
    final String pattern = FileUtilRt.getExtension(fileName).toLowerCase();
    if (context.contains(pattern)) return false;
    int rc = Messages.showOkCancelDialog(project,
                                 VcsBundle.message("diff.unknown.file.type.prompt", fileName),
                                 VcsBundle.message("diff.unknown.file.type.title"),
                                   VcsBundle.message("diff.unknown.file.type.associate"),
                                   CommonBundle.getCancelButtonText(),
                                 Messages.getQuestionIcon());
    if (rc == Messages.OK) {
      FileType fileType = FileTypeChooser.associateFileType(fileName);
      return fileType != null && !fileType.isBinary();
    } else {
      context.add(pattern);
    }
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ChangeDiffRequestPresentable that = (ChangeDiffRequestPresentable)o;

    if (myChange != null ? !myChange.equals(that.myChange) : that.myChange != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myChange != null ? myChange.hashCode() : 0;
  }
}
