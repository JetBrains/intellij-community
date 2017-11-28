package com.intellij.openapi.vcs.changes.actions.migrate;

import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContentImpl;
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.contents.FileContentImpl;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.ErrorDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.LineCol;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.diff.DiffTool;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.notNullize;

public class MigrateToNewDiffUtil {
  @NotNull
  public static DiffRequest convertRequest(@NotNull com.intellij.openapi.diff.DiffRequest oldRequest) {
    DiffRequest request = convertRequestFair(oldRequest);
    if (request != null) return request;

    return new ErrorDiffRequest(new MyDiffRequestProducer(oldRequest), "Can't convert from old-style request");
  }

  @Nullable
  private static DiffRequest convertRequestFair(@NotNull com.intellij.openapi.diff.DiffRequest oldRequest) {
    if (oldRequest.getOnOkRunnable() != null) return null;
    //if (oldRequest.getBottomComponent() != null) return null; // TODO: we need EDT to make this check. Let's ignore bottom component.
    // TODO: migrate layers

    com.intellij.openapi.diff.DiffContent[] contents = oldRequest.getContents();
    String[] titles = oldRequest.getContentTitles();
    List<DiffContent> newContents = new ArrayList<>(contents.length);

    for (int i = 0; i < contents.length; i++) {
      DiffContent convertedContent = convertContent(oldRequest.getProject(), contents[i]);
      if (convertedContent == null) return null;
      newContents.add(convertedContent);
    }

    SimpleDiffRequest newRequest = new SimpleDiffRequest(oldRequest.getWindowTitle(), newContents, Arrays.asList(titles));

    DiffNavigationContext navigationContext = (DiffNavigationContext)oldRequest.getGenericData().get(DiffTool.SCROLL_TO_LINE.getName());
    if (navigationContext != null) {
      newRequest.putUserData(DiffUserDataKeysEx.NAVIGATION_CONTEXT, navigationContext);
    }

    return newRequest;
  }

  @Nullable
  private static DiffContent convertContent(@Nullable Project project, @NotNull final com.intellij.openapi.diff.DiffContent oldContent) {
    if (oldContent.isEmpty()) {
      return new EmptyContent();
    }
    if (oldContent.isBinary()) {
      VirtualFile file = oldContent.getFile();
      if (file == null) return null;
      return new FileContentImpl(project, file);
    }
    else {
      Document document = oldContent.getDocument();
      if (document == null) return null;
      return new DocumentContentImpl(project, document, oldContent.getContentType(), oldContent.getFile(), oldContent.getLineSeparator(), null, null) {
        @Nullable
        @Override
        public Navigatable getNavigatable(@NotNull LineCol position) {
          return oldContent.getOpenFileDescriptor(position.toOffset(document));
        }

        @Override
        public void onAssigned(boolean isAssigned) {
          oldContent.onAssigned(isAssigned);
        }
      };
    }
  }

  private static class MyDiffRequestProducer implements DiffRequestProducer {
    @NotNull private final com.intellij.openapi.diff.DiffRequest myRequest;

    public MyDiffRequestProducer(@NotNull com.intellij.openapi.diff.DiffRequest request) {
      myRequest = request;
    }

    @NotNull
    @Override
    public String getName() {
      return notNullize(myRequest.getWindowTitle());
    }

    @NotNull
    @Override
    public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
      throws ProcessCanceledException {
      return new ErrorDiffRequest(this, "Can't convert from old-style request");
    }
  }
}
