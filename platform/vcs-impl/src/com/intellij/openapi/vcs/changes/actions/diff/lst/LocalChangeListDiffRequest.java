// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst;

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LocalChangeListDiffRequest extends ContentDiffRequest {
  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myVirtualFile;
  @NotNull private final String myChangelistId;
  @NotNull private final String myChangelistName;
  @NotNull private final ContentDiffRequest myRequest;

  private int myAssignments;
  private boolean myInstalled;

  public LocalChangeListDiffRequest(@NotNull Project project,
                                    @NotNull VirtualFile virtualFile,
                                    @NotNull String changelistId,
                                    @NotNull String changelistName,
                                    @NotNull ContentDiffRequest request) {
    myProject = project;
    myVirtualFile = virtualFile;
    myChangelistId = changelistId;
    myChangelistName = changelistName;
    myRequest = request;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  @NotNull
  public String getChangelistId() {
    return myChangelistId;
  }

  @NotNull
  public String getChangelistName() {
    return myChangelistName;
  }

  @NotNull
  public ContentDiffRequest getRequest() {
    return myRequest;
  }

  @Nullable
  public LineStatusTracker getLineStatusTracker() {
    return LineStatusTrackerManager.getInstance(myProject).getLineStatusTracker(myVirtualFile);
  }


  @Nullable
  @Override
  public String getTitle() {
    return String.format("%s [%s]", myRequest.getTitle(), myChangelistName);
  }

  @NotNull
  @Override
  public List<DiffContent> getContents() {
    return myRequest.getContents();
  }

  @NotNull
  @Override
  public List<String> getContentTitles() {
    List<String> titles = myRequest.getContentTitles();
    String title1 = titles.get(0);
    String title2 = titles.get(1);
    String ourTitle2 = title2 != null ? String.format("%s in %s", title2, myChangelistName) : null;
    return ContainerUtil.list(title1, ourTitle2);
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return myRequest.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myRequest.putUserData(key, value);
  }


  @Override
  @CalledInAwt
  public void onAssigned(boolean isAssigned) {
    myRequest.onAssigned(isAssigned);

    if (isAssigned) {
      if (!myInstalled) {
        myInstalled = installTracker();
      }
      myAssignments++;
    }
    else {
      if (myAssignments == 1 && myInstalled) {
        releaseTracker();
        myInstalled = false;
      }
      myAssignments--;
    }

    assert myAssignments >= 0;
  }

  private boolean installTracker() {
    Document document = FileDocumentManager.getInstance().getDocument(myVirtualFile);
    if (document == null) return false;

    LineStatusTrackerManager.getInstance(myProject).requestTrackerFor(document, this);
    return true;
  }

  private void releaseTracker() {
    Document document = FileDocumentManager.getInstance().getCachedDocument(myVirtualFile);
    if (document == null) return;

    LineStatusTrackerManager.getInstance(myProject).releaseTrackerFor(document, this);
  }
}
