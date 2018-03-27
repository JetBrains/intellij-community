/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vfs.VirtualFile;
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
    return myRequest.getContentTitles();
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
