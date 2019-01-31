// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import git4idea.diff.GitSubmoduleContentRevision;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.repo.GitSubmodule;
import git4idea.repo.GitSubmoduleKt;
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

  @Override
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

  @Override
  @NotNull
  public FilePath getFile() {
    return myFile;
  }

  @Override
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
    return createRevision(file, revisionNumber, project, null);
  }

  @Nullable
  public static GitSubmodule getRepositoryIfSubmodule(@NotNull Project project, @NotNull FilePath path) {
    VirtualFile file = path.getVirtualFile();
    if (file == null) { // NB: deletion of a submodule is not supported yet
      return null;
    }
    if (!file.isDirectory()) {
      return null;
    }

    GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
    GitRepository candidate = repositoryManager.getRepositoryForRoot(file);
    if (candidate == null) { // not a root
      return null;
    }
    return GitSubmoduleKt.asSubmodule(candidate);
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
    return createRevision(filePath, revisionNumber, project, null);
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
    GitSubmodule submodule = getRepositoryIfSubmodule(project, filePath);
    if (revisionNumber != null && revisionNumber != VcsRevisionNumber.NULL) {
      if (submodule != null) {
        return GitSubmoduleContentRevision.createRevision(submodule, revisionNumber);
      }
      return createRevisionImpl(filePath, (GitRevisionNumber)revisionNumber, project, charset);
    }
    else if (submodule != null) {
      return GitSubmoduleContentRevision.createCurrentRevision(submodule.getRepository());
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
