/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import static com.intellij.openapi.vcs.impl.ContentRevisionCache.UniqueType.REPOSITORY_CONTENT;

public class GitContentRevision implements ByteBackedContentRevision {
  @NotNull protected final FilePath myFile;
  @NotNull private final GitRevisionNumber myRevision;
  @NotNull private final Project myProject;
  @Nullable private final Charset myCharset;

  protected GitContentRevision(@NotNull FilePath file,
                               @NotNull GitRevisionNumber revision,
                               @NotNull Project project,
                               @Nullable Charset charset) {
    myProject = project;
    myFile = file;
    myRevision = revision;
    myCharset = charset;
  }

  @Nullable
  public String getContent() throws VcsException {
    byte[] bytes = getContentAsBytes();
    if (bytes == null) return null;
    return ContentRevisionCache.getAsString(bytes, myFile, myCharset);
  }

  @Nullable
  @Override
  public byte[] getContentAsBytes() throws VcsException {
    if (myFile.isDirectory()) {
      return null;
    }
    try {
      return ContentRevisionCache.getOrLoadAsBytes(myProject, myFile, myRevision, GitVcs.getKey(), REPOSITORY_CONTENT, this::loadContent);
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  private byte[] loadContent() throws VcsException {
    VirtualFile root = GitUtil.getGitRoot(myFile);
    return GitFileUtils.getFileContent(myProject, root, myRevision.getRev(), VcsFileUtil.relativePath(root, myFile));
  }

  @NotNull
  public FilePath getFile() {
    return myFile;
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return myRevision;
  }

  public boolean equals(Object obj) {
    if (this == obj) return true;
    if ((obj == null) || (obj.getClass() != getClass())) return false;

    GitContentRevision test = (GitContentRevision)obj;
    return (myFile.equals(test.myFile) && myRevision.equals(test.myRevision));
  }

  public int hashCode() {
    return myFile.hashCode() + myRevision.hashCode();
  }

  /**
   * Create revision
   *
   *
   * @param vcsRoot        a vcs root for the repository
   * @param path           an path inside with possibly escape sequences
   * @param revisionNumber a revision number, if null the current revision will be created
   * @param project        the context project
   * @param unescapePath
   * @return a created revision
   * @throws VcsException if there is a problem with creating revision
   */
  @NotNull
  public static ContentRevision createRevision(@NotNull VirtualFile vcsRoot,
                                               @NotNull String path,
                                               @Nullable VcsRevisionNumber revisionNumber,
                                               Project project,
                                               boolean unescapePath) throws VcsException {
    FilePath file = createPath(vcsRoot, path, unescapePath);
    return createRevision(file, revisionNumber, project);
  }

  @NotNull
  private static ContentRevision createRevision(@NotNull FilePath filePath,
                                                @Nullable VcsRevisionNumber revisionNumber,
                                                @NotNull Project project) {
    if (revisionNumber != null && revisionNumber != VcsRevisionNumber.NULL) {
      return createRevisionImpl(filePath, (GitRevisionNumber)revisionNumber, project, null);
    }
    else {
      return CurrentContentRevision.create(filePath);
    }
  }

  @NotNull
  public static ContentRevision createRevisionForTypeChange(@NotNull Project project,
                                                            @NotNull VirtualFile vcsRoot,
                                                            @NotNull String path,
                                                            @Nullable VcsRevisionNumber revisionNumber,
                                                            boolean unescapePath) throws VcsException {
    FilePath filePath;
    if (revisionNumber == null) {
      File file = new File(makeAbsolutePath(vcsRoot, path, unescapePath));
      VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
      filePath = virtualFile == null ? VcsUtil.getFilePath(file, false) : VcsUtil.getFilePath(virtualFile);
    } else {
      filePath = createPath(vcsRoot, path, unescapePath);
    }
    return createRevision(filePath, revisionNumber, project);
  }

  @NotNull
  public static FilePath createPath(@NotNull VirtualFile vcsRoot,
                                    @NotNull String path,
                                    boolean unescapePath) throws VcsException {
    String absolutePath = makeAbsolutePath(vcsRoot, path, unescapePath);
    return VcsUtil.getFilePath(absolutePath, false);
  }

  @NotNull
  private static String makeAbsolutePath(@NotNull VirtualFile vcsRoot, @NotNull String path, boolean unescapePath) throws VcsException {
    String unescapedPath = unescapePath ? GitUtil.unescapePath(path) : path;
    return vcsRoot.getPath() + "/" + unescapedPath;
  }

  @NotNull
  public static ContentRevision createRevision(@NotNull VirtualFile file,
                                               @Nullable VcsRevisionNumber revisionNumber,
                                               @NotNull Project project) {
    FilePath filePath = VcsUtil.getFilePath(file);
    return createRevision(filePath, revisionNumber, project, null);
  }

  @NotNull
  public static ContentRevision createRevision(@NotNull FilePath filePath,
                                               @Nullable VcsRevisionNumber revisionNumber,
                                               @NotNull Project project,
                                               @Nullable Charset charset) {
    if (revisionNumber != null && revisionNumber != VcsRevisionNumber.NULL) {
      return createRevisionImpl(filePath, (GitRevisionNumber)revisionNumber, project, charset);
    }
    else {
      return CurrentContentRevision.create(filePath);
    }
  }

  @NotNull
  private static GitContentRevision createRevisionImpl(@NotNull FilePath path,
                                                       @NotNull GitRevisionNumber revisionNumber,
                                                       @NotNull Project project,
                                                       @Nullable Charset charset) {
    if (path.getFileType().isBinary()) {
      return new GitBinaryContentRevision(path, revisionNumber, project);
    } else {
      return new GitContentRevision(path, revisionNumber, project, charset);
    }
  }

  @Override
  public String toString() {
    return myFile.getPath();
  }
}
