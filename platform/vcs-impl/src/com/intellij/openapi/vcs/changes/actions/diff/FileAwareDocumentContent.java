package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContentImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

public class FileAwareDocumentContent extends DocumentContentImpl {
  @Nullable private final Project myProject;
  @Nullable private final VirtualFile myLocalFile;

  public FileAwareDocumentContent(@Nullable Project project,
                                  @NotNull Document document,
                                  @Nullable FileType fileType,
                                  @Nullable VirtualFile localFile,
                                  @Nullable LineSeparator separator,
                                  @Nullable Charset charset) {
    super(document, fileType, localFile, separator, charset);
    myProject = project;
    myLocalFile = localFile;
  }

  public OpenFileDescriptor getOpenFileDescriptor(int offset) {
    if (myProject == null || myLocalFile == null) return null;
    return new OpenFileDescriptor(myProject, myLocalFile, offset);
  }

  @NotNull
  public static DiffContent create(@Nullable Project project,
                                   @NotNull String content,
                                   @NotNull FilePath path) {
    VirtualFile localFile = LocalFileSystem.getInstance().findFileByPath(path.getPath());
    FileType fileType = localFile != null ? localFile.getFileType() : path.getFileType();
    Charset charset = localFile != null ? localFile.getCharset() : path.getCharset(project);
    return create(project, content, fileType, localFile, charset);
  }

  @NotNull
  public static DiffContent create(@Nullable Project project,
                                   @NotNull String content,
                                   @NotNull VirtualFile file) {
    FileType fileType = file.getFileType();
    Charset charset = file.getCharset();
    return create(project, content, fileType, file, charset);
  }

  @NotNull
  private static DiffContent create(@Nullable Project project,
                                    @NotNull String content,
                                    @Nullable FileType fileType,
                                    @Nullable VirtualFile file,
                                    @Nullable Charset charset) {
    LineSeparator separator = StringUtil.detectSeparators(content);
    Document document = EditorFactory.getInstance().createDocument(StringUtil.convertLineSeparators(content));
    document.setReadOnly(true);
    if (FileTypes.UNKNOWN.equals(fileType)) fileType = PlainTextFileType.INSTANCE;
    return new FileAwareDocumentContent(project, document, fileType, file, separator, charset);
  }
}
