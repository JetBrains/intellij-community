// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TestDiffRequestProcessor extends DiffRequestProcessor {
  @NotNull private final List<? extends DiffHyperlink> myRequests;
  private int myIndex;

  public TestDiffRequestProcessor(@Nullable Project project, @NotNull List<? extends DiffHyperlink> requests, int index) {
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

      String title1 = file1 != null ? ExecutionBundle.message("diff.content.expected.title.with.file.url", file1.getPresentableUrl())
                                    : ExecutionBundle.message("diff.content.expected.title");
      String title2 = file2 != null ? ExecutionBundle.message("diff.content.actual.title.with.file.url", file2.getPresentableUrl())
                                    : ExecutionBundle.message("diff.content.actual.title");

      return new SimpleDiffRequest(windowTitle, content1, content2, title1, title2);
    }
    catch (Exception e) {
      return new ErrorDiffRequest(e);
    }
  }

  @Nullable
  private static VirtualFile findFile(@Nullable String path) {
    if (path == null) return null;
    NewVirtualFileSystem fs = path.contains(URLUtil.JAR_SEPARATOR) ? JarFileSystem.getInstance() : LocalFileSystem.getInstance();
    return fs.refreshAndFindFileByPath(path);
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

  //
  // Navigation
  //

  @Override
  protected boolean hasNextChange(boolean fromUpdate) {
    return myIndex + 1 < myRequests.size();
  }

  @Override
  protected boolean hasPrevChange(boolean fromUpdate) {
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
