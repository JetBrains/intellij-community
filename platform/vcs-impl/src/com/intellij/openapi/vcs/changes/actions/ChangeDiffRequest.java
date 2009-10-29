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
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NullableFactory;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeRequestChain;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author yole
 */
public class ChangeDiffRequest implements ChangeRequestChain {
  private final List<Change> myChanges;
  private int myIndex;
  
  private final ShowDiffAction.DiffExtendUIFactory myActionsFactory;

  private final AnAction myPrevChangeAction;
  private final AnAction myNextChangeAction;
  private final Project myProject;

  public ChangeDiffRequest(final Project project, final List<Change> changes, final ShowDiffAction.DiffExtendUIFactory actionsFactory) {
    myProject = project;
    myChanges = changes;

    myIndex = 0;
    myActionsFactory = actionsFactory;

    myPrevChangeAction = ActionManager.getInstance().getAction("Diff.PrevChange");
    myNextChangeAction = ActionManager.getInstance().getAction("Diff.NextChange");
  }

  public boolean quickCheckHaveStuff() {
    if (myChanges.isEmpty()) return false;
    if (myChanges.size() == 1) {
      final Change change = myChanges.get(0);
      return checkContentsAvailable(change.getBeforeRevision(), change.getAfterRevision());
    }
    return true;
  }

  @Nullable
  public SimpleDiffRequest init(final int idx) {
    if (idx < 0 || idx > (myChanges.size() - 1)) return null;
    myIndex = idx - 1;
    return moveForward();
  }

  public boolean canMoveForward() {
    return myIndex < (myChanges.size() - 1);
  }

  public boolean canMoveBack() {
    return myIndex > 0;
  }

  @Nullable
  public SimpleDiffRequest moveForward() {
    return moveImpl(new MoveDirection() {
      public boolean canMove() {
        return canMoveForward();
      }
      public int direction() {
        return 1;
      }
    });
  }

  @Nullable
  private SimpleDiffRequest moveImpl(final MoveDirection moveDirection) {
    while (moveDirection.canMove()) {
      final int nextIdx = myIndex + moveDirection.direction();
      final Change change = myChanges.get(nextIdx);
      final SimpleDiffRequest request = new SimpleDiffRequest(myProject, null);
      final MyReturnValue returnValue = getRequestForChange(request, change);
      if (MyReturnValue.quit.equals(returnValue)) {
        return null;
      }
      if (MyReturnValue.removeFromList.equals(returnValue)) {
        myChanges.remove(nextIdx);
        continue;
      }
      myIndex = nextIdx;
      return request;
    }
    return null;
  }

  private interface MoveDirection {
    boolean canMove();
    int direction();
  }

  @Nullable
  public SimpleDiffRequest moveBack() {
    return moveImpl(new MoveDirection() {
      public boolean canMove() {
        return canMoveBack();
      }
      public int direction() {
        return -1;
      }
    });
  }

  private static enum MyReturnValue {
    removeFromList,
    useRequest,
    quit;
  }

  @Nullable
  private MyReturnValue getRequestForChange(final SimpleDiffRequest request, final Change change) {
    if (! canShowChange(change)) return MyReturnValue.removeFromList;
    if (! loadCurrentContents(request, change)) return MyReturnValue.quit;
    takeStuffFromFactory(request, change);
    return MyReturnValue.useRequest;
  }

  private void takeStuffFromFactory(final SimpleDiffRequest request, final Change change) {
    if (myChanges.size() > 1 || (myActionsFactory != null)) {
      request.setToolbarAddons(new DiffRequest.ToolbarAddons() {
        public void customize(DiffToolbar toolbar) {
          if (myChanges.size() > 1)
          toolbar.addSeparator();
          toolbar.addAction(myPrevChangeAction);
          toolbar.addAction(myNextChangeAction);

          if (myActionsFactory != null) {
            toolbar.addSeparator();
            for (AnAction action : myActionsFactory.createActions(change)) {
              toolbar.addAction(action);
            }
          }
        }
      });
    }

    if (myActionsFactory != null) {
      request.setBottomComponentFactory(new NullableFactory<JComponent>() {
        public JComponent create() {
          return myActionsFactory.createBottomComponent();
        }
      });
    }
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
    if (beforeRevisionTitle.length() == 0) {
      beforeRevisionTitle = "Base version";
    }
    if (afterRevisionTitle.length() == 0) {
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

  private boolean canShowChange(final Change change) {
    final ContentRevision bRev = change.getBeforeRevision();
    final ContentRevision aRev = change.getAfterRevision();

    if ((bRev != null && (bRev.getFile().getFileType().isBinary() || bRev.getFile().isDirectory())) ||
        (aRev != null && (aRev.getFile().getFileType().isBinary() || aRev.getFile().isDirectory()))) {
      if (bRev != null && bRev.getFile().getFileType() == FileTypes.UNKNOWN && !bRev.getFile().isDirectory()) {
        if (! checkContentsAvailable(bRev, aRev)) return false;
        if (!checkAssociate(myProject, bRev.getFile())) return false;
      }
      else if (aRev != null && aRev.getFile().getFileType() == FileTypes.UNKNOWN && !aRev.getFile().isDirectory()) {
        if (! checkContentsAvailable(bRev, aRev)) return false;
        if (!checkAssociate(myProject, aRev.getFile())) return false;
      }
      else {
        return false;
      }
    }
    return true;
  }

  private static boolean checkContentsAvailable(@Nullable final ContentRevision bRev, @Nullable final ContentRevision aRev) {
    String bContents = null;
    if (bRev != null) {
      try {
        bContents = bRev.getContent();
      } catch (VcsException e) {
        //
      }
    }
    String aContents = null;
    if (aRev != null) {
      try {
        aContents = aRev.getContent();
      } catch (VcsException e) {
        //
      }
    }
    return (bContents != null) || (aContents != null);
  }

  private static boolean checkAssociate(final Project project, final FilePath file) {
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
    }
    return false;
  }
}
