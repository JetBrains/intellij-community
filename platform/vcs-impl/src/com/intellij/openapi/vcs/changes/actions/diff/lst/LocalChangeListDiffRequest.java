// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private final @NotNull Project myProject;
  private final @NotNull VirtualFile myVirtualFile;
  private final @NotNull String myChangelistId;
  private final @NotNull @NlsSafe String myChangelistName;
  private final @NotNull ContentDiffRequest myRequest;

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

  public @NotNull Project getProject() {
    return myProject;
  }

  public @NotNull VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  public @NotNull String getChangelistId() {
    return myChangelistId;
  }

  public @NotNull String getChangelistName() {
    return myChangelistName;
  }

  public @NotNull ContentDiffRequest getRequest() {
    return myRequest;
  }

  public @Nullable LineStatusTracker getLineStatusTracker() {
    return LineStatusTrackerManager.getInstance(myProject).getLineStatusTracker(myVirtualFile);
  }


  @Override
  public @NlsContexts.DialogTitle @Nullable String getTitle() {
    return VcsBundle.message("change.dialog.title.change.list.name", myRequest.getTitle(), myChangelistName);
  }

  @Override
  public @NotNull List<DiffContent> getContents() {
    return myRequest.getContents();
  }

  @Override
  public @NotNull List<@Nls String> getContentTitles() {
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
