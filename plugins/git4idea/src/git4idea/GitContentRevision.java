// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Nullable
  public Charset getCharset() {
    return myCharset;
  }

  @Override
  @Nullable
  public String getContent() throws VcsException {
    byte[] bytes = getContentAsBytes();
    if (bytes == null) return null;
    return ContentRevisionCache.getAsString(bytes, myFile, myCharset);
  }

  @Override
  public byte @Nullable [] getContentAsBytes() throws VcsException {
    if (myFile.isDirectory()) {
      return null;
    }
    try {
      if (!GitUtil.isHashString(myRevision.getRev())) {
        // do not cache contents for 'HEAD' or branch/tag references
        return ContentRevisionCache.loadAsBytes(myFile, this::loadContent);
      }
      return ContentRevisionCache.getOrLoadAsBytes(myProject, myFile, myRevision, GitVcs.getKey(), REPOSITORY_CONTENT, this::loadContent);
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  private byte @NotNull [] loadContent() throws VcsException {
    VirtualFile root = GitUtil.getRootForFile(myProject, myFile);
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

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || obj.getClass() != getClass()) return false;

    GitContentRevision test = (GitContentRevision)obj;
    return myFile.equals(test.myFile) && myRevision.equals(test.myRevision);
  }

  @Override
  public int hashCode() {
    return myFile.hashCode() + myRevision.hashCode();
  }

  @Nullable
  public static GitSubmodule getRepositoryIfSubmodule(@NotNull Project project, @NotNull FilePath path) {
    // NB: deletion of a submodule is not supported yet
    GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
    GitRepository candidate = repositoryManager.getRepositoryForRootQuick(path);
    if (candidate == null) { // not a root
      return null;
    }
    return GitSubmoduleKt.asSubmodule(candidate);
  }

  @NotNull
  public static ContentRevision createRevisionForTypeChange(@NotNull FilePath filePath,
                                                            @Nullable VcsRevisionNumber revisionNumber,
                                                            @NotNull Project project) {
    if (revisionNumber == null) {
      File file = filePath.getIOFile();
      VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
      if (virtualFile != null) filePath = VcsUtil.getFilePath(virtualFile);
    }
    return createRevision(filePath, revisionNumber, project);
  }

  @NotNull
  public static FilePath createPathFromEscaped(@NotNull VirtualFile vcsRoot, @NotNull String path) throws VcsException {
    return createPathFromEscaped(vcsRoot, path, false);
  }

  @NotNull
  public static FilePath createPathFromEscaped(@NotNull VirtualFile vcsRoot, @NotNull String path, boolean isDirectory)
    throws VcsException {
    String absolutePath = makeAbsolutePath(vcsRoot, GitUtil.unescapePath(path));
    return VcsUtil.getFilePath(absolutePath, isDirectory);
  }

  @NotNull
  public static FilePath createPath(@NotNull VirtualFile vcsRoot, @NotNull String unescapedPath) {
    return createPath(vcsRoot, unescapedPath, false);
  }

  @NotNull
  public static FilePath createPath(@NotNull VirtualFile vcsRoot, @NotNull String unescapedPath, boolean isDirectory) {
    String absolutePath = makeAbsolutePath(vcsRoot, unescapedPath);
    return VcsUtil.getFilePath(absolutePath, isDirectory);
  }

  @NotNull
  private static String makeAbsolutePath(@NotNull VirtualFile vcsRoot, @NotNull String unescapedPath) {
    return vcsRoot.getPath() + "/" + unescapedPath;
  }

  @NotNull
  public static ContentRevision createRevision(@NotNull FilePath filePath,
                                               @Nullable VcsRevisionNumber revisionNumber,
                                               @NotNull Project project) {
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
      return new GitContentRevision(filePath, (GitRevisionNumber)revisionNumber, project, charset);
    }
    else if (submodule != null) {
      return GitSubmoduleContentRevision.createCurrentRevision(submodule.getRepository());
    }
    else {
      return new CurrentContentRevision(filePath);
    }
  }

  @Override
  public String toString() {
    return myFile.getPath();
  }
}
