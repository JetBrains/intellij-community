// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst;

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class LocalChangeListDiffRequest extends ContentDiffRequest {
  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myVirtualFile;
  @NotNull private final String myChangelistId;
  @NotNull private final @NlsSafe String myChangelistName;
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


  @NlsContexts.DialogTitle
  @Nullable
  @Override
  public String getTitle() {
    return VcsBundle.message("change.dialog.title.change.list.name", myRequest.getTitle(), myChangelistName);
  }

  @NotNull
  @Override
  public List<DiffContent> getContents() {
    return myRequest.getContents();
  }

  @NotNull
  @Override
  public List<@Nls String> getContentTitles() {
    List<String> titles = myRequest.getContentTitles();
    String title1 = titles.get(0);
    String title2 = titles.get(1);
    @Nls String ourTitle2 = title2 != null ? VcsBundle.message("change.dialog.title.in.change.list.name", title2, myChangelistName) : null;
    return Arrays.asList(title1, ourTitle2);
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
  @RequiresEdt
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

    DocumentContent beforeContent = (DocumentContent)getContents().get(0);
    CharSequence beforeText = beforeContent.getDocument().getImmutableCharSequence();
    LineStatusTrackerManager.getInstanceImpl(myProject).offerTrackerContent(document, beforeText);
    return true;
  }

  private void releaseTracker() {
    Document document = FileDocumentManager.getInstance().getCachedDocument(myVirtualFile);
    if (document == null) return;

    LineStatusTrackerManager.getInstance(myProject).releaseTrackerFor(document, this);
  }
}
