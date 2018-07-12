package com.intellij.openapi.vcs.changes.actions.migrate;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
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
}
