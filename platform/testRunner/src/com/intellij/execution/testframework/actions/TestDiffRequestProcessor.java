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
import com.intellij.diff.util.DiffUserDataKeysEx;
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
    super(project);
    myRequests = requests;
    myIndex = index;

    putContextUserData(DiffUserDataKeys.DO_NOT_IGNORE_WHITESPACES, true);
    putContextUserData(DiffUserDataKeysEx.PLACE, DiffPlaces.TESTS_FAILED_ASSERTIONS);
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
      String title = hyperlink.getDiffTitle();

      String title1;
      String title2 = ExecutionBundle.message("diff.content.actual.title");
      DiffContent content1;
      DiffContent content2 = DiffContentFactory.getInstance().create(hyperlink.getRight(), null);

      String filePath = hyperlink.getFilePath();
      final VirtualFile vFile;
      if (filePath != null && (vFile = LocalFileSystem.getInstance().findFileByPath(filePath)) != null) {
        title1 = ExecutionBundle.message("diff.content.expected.title") + " (" + vFile.getPresentableUrl() + ")";
        content1 = DiffContentFactory.getInstance().create(getProject(), vFile);
      }
      else {
        title1 = ExecutionBundle.message("diff.content.expected.title");
        content1 = DiffContentFactory.getInstance().create(hyperlink.getLeft(), null);
      }

      return new SimpleDiffRequest(title, content1, content2, title1, title2);
    }
    catch (Exception e) {
      return new ErrorDiffRequest(e);
    }
  }

  //
  // Navigation
  //

  @Override
  protected boolean hasNextChange() {
    return true; // TODO: disable looping ?
  }

  @Override
  protected boolean hasPrevChange() {
    return true;
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
