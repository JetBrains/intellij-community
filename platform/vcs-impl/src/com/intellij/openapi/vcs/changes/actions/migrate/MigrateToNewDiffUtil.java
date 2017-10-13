package com.intellij.openapi.vcs.changes.actions.migrate;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.ErrorDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.notNullize;
import static com.intellij.util.ObjectUtils.assertNotNull;

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

    com.intellij.openapi.diff.DiffContent[] contents = oldRequest.getContents();
    String[] titles = oldRequest.getContentTitles();
    List<DiffContent> newContents = new ArrayList<>(contents.length);

    for (int i = 0; i < contents.length; i++) {
      newContents.add(convertContent(oldRequest.getProject(), contents[i]));
    }

    return new SimpleDiffRequest(oldRequest.getWindowTitle(), newContents, Arrays.asList(titles));
  }

  @NotNull
  private static DiffContent convertContent(@Nullable Project project, @NotNull final com.intellij.openapi.diff.DiffContent oldContent) {
    DiffContentFactory factory = DiffContentFactory.getInstance();
    if (oldContent.isEmpty()) {
      return factory.createEmpty();
    }
    if (oldContent instanceof com.intellij.openapi.diff.FileContent) {
      VirtualFile file = assertNotNull(oldContent.getFile());
      return factory.create(project, file);
    }
    else if (oldContent instanceof com.intellij.openapi.diff.SimpleContent) {
      return factory.create(project, ((SimpleContent)oldContent).getText(), oldContent.getContentType());
    }
    else {
      Document document = assertNotNull(oldContent.getDocument());
      return factory.create(project, document, oldContent.getContentType());
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
