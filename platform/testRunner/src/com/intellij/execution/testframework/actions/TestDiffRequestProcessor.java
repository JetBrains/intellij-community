/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.testframework.actions;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.impl.DiffRequestProcessor;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.ErrorDiffRequest;
import com.intellij.diff.requests.NoDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffPlaces;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.testframework.stacktrace.DiffHyperlink;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TestDiffRequestProcessor extends DiffRequestProcessor {
  @NotNull private final List<DiffHyperlink> myRequests;
  private int myIndex;

  public TestDiffRequestProcessor(@Nullable Project project, @NotNull List<DiffHyperlink> requests, int index) {
    super(project, DiffPlaces.TESTS_FAILED_ASSERTIONS);
    myRequests = requests;
    myIndex = index;

    putContextUserData(DiffUserDataKeys.DO_NOT_IGNORE_WHITESPACES, true);
  }

  //
  // Impl
  //

  @Override
  public void updateRequest(boolean force, @Nullable ScrollToPolicy scrollToChangePolicy) {
    applyRequest(loadRequest(), force, scrollToChangePolicy);
  }

  @NotNull
  private DiffRequest loadRequest() {
    if (myIndex < 0 || myIndex >= myRequests.size()) return NoDiffRequest.INSTANCE;
    DiffHyperlink hyperlink = myRequests.get(myIndex);
    try {
      String windowTitle = hyperlink.getDiffTitle();

      String text1 = hyperlink.getLeft();
      String text2 = hyperlink.getRight();
      VirtualFile file1 = findFile(hyperlink.getFilePath());
      VirtualFile file2 = findFile(hyperlink.getActualFilePath());

      DiffContent content1 = createContentWithTitle(getProject(), text1, file1, file2);
      DiffContent content2 = createContentWithTitle(getProject(), text2, file2, file1);

      String title1 = getContentTitle("diff.content.expected.title", file1);
      String title2 = getContentTitle("diff.content.actual.title", file2);

      return new SimpleDiffRequest(windowTitle, content1, content2, title1, title2);
    }
    catch (Exception e) {
      return new ErrorDiffRequest(e);
    }
  }

  @Nullable
  private static VirtualFile findFile(@Nullable String path) {
    return path != null ? LocalFileSystem.getInstance().findFileByPath(path) : null;
  }

  @NotNull
  private static DiffContent createContentWithTitle(@Nullable Project project,
                                                    @NotNull String content,
                                                    @Nullable VirtualFile contentFile,
                                                    @Nullable VirtualFile highlightFile) {
    if (contentFile != null) {
      return DiffContentFactory.getInstance().create(project, contentFile);
    }
    else {
      return DiffContentFactory.getInstance().create(project, content, highlightFile);
    }
  }

  @NotNull
  private static String getContentTitle(@NotNull String titleKey, @Nullable VirtualFile file) {
    String title = ExecutionBundle.message(titleKey);
    if (file != null) {
      title += " (" + file.getPresentableUrl() + ")";
    }
    return title;
  }

  //
  // Navigation
  //

  @Override
  protected boolean hasNextChange() {
    return myIndex + 1 < myRequests.size();
  }

  @Override
  protected boolean hasPrevChange() {
    return myIndex > 0;
  }

  @Override
  protected void goToNextChange(boolean fromDifferences) {
    myIndex++;
    if (myIndex >= myRequests.size()) myIndex = 0;

    updateRequest(false, fromDifferences ? ScrollToPolicy.FIRST_CHANGE : null);
  }

  @Override
  protected void goToPrevChange(boolean fromDifferences) {
    myIndex--;
    if (myIndex < 0) myIndex = myRequests.size() - 1;

    updateRequest(false, fromDifferences ? ScrollToPolicy.LAST_CHANGE : null);
  }

  @Override
  protected boolean isNavigationEnabled() {
    return myRequests.size() > 1;
  }
}
