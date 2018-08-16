package com.intellij.openapi.vcs.changes.actions.migrate;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.InvalidDiffRequestException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.impl.mergeTool.MergeRequestImpl;
import com.intellij.openapi.diff.impl.mergeTool.MergeVersion;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.util.ObjectUtils.assertNotNull;

public class MigrateToNewDiffUtil {
  @NotNull
  public static DiffRequest convertRequest(@NotNull com.intellij.openapi.diff.DiffRequest oldRequest) {
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

  @NotNull
  public static MergeRequest convertMergeRequest(@NotNull MergeRequestImpl request) throws InvalidDiffRequestException {
    MergeRequestImpl.MergeContent mergeContent = assertNotNull(request.getMergeContent());
    MergeVersion.MergeDocumentVersion mergeVersion = (MergeVersion.MergeDocumentVersion)mergeContent.getMergeVersion();

    com.intellij.openapi.diff.SimpleContent leftContent = (SimpleContent)request.getContents()[0];
    com.intellij.openapi.diff.SimpleContent rightContent = (SimpleContent)request.getContents()[2];
    List<String> contents = ContainerUtil.list(leftContent.getText(), mergeVersion.getOriginalText(), rightContent.getText());

    Document document = mergeContent.getDocument();

    String windowTitle = request.getWindowTitle();
    List<String> titles = Arrays.asList(request.getContentTitles());

    Consumer<MergeResult> callback = result -> {
      request.setResult(result == MergeResult.CANCEL ? DialogWrapper.CANCEL_EXIT_CODE : DialogWrapper.OK_EXIT_CODE);
    };

    return DiffRequestFactory.getInstance().createMergeRequest(request.getProject(), mergeContent.getContentType(), document, contents,
                                                               windowTitle, titles, callback);
  }
}
