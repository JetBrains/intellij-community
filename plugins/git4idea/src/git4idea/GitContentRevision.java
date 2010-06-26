/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * Git content revision
 */
public class GitContentRevision implements ContentRevision {
  /**
   * the file path
   */
  @NotNull private final FilePath myFile;
  /**
   * the revision number
   */
  @NotNull private final GitRevisionNumber myRevision;
  /**
   * the context project
   */
  @NotNull private final Project myProject;
  /**
   * The charset for the file
   */
  @NotNull private final Charset myCharset;

  public GitContentRevision(@NotNull FilePath file, @NotNull GitRevisionNumber revision, @NotNull Project project, Charset charset) {
    myProject = project;
    myFile = file;
    myRevision = revision;
    myCharset = charset != null ? charset : file.getCharset(project);
  }

  public GitContentRevision(@NotNull FilePath file, @NotNull GitRevisionNumber revision, @NotNull Project project) {
    this(file, revision, project, null);
  }

  @Nullable
  public String getContent() throws VcsException {
    if (myFile.isDirectory()) {
      return null;
    }
    VirtualFile root = GitUtil.getGitRoot(myFile);
    GitSimpleHandler h = new GitSimpleHandler(myProject, root, GitCommand.SHOW);
    h.setCharset(myCharset);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters(myRevision.getRev() + ":" + GitUtil.relativePath(root, myFile));
    return h.run();
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
   * @param vcsRoot        a vcs root for the repository
   * @param path           an path inside with possibly escape sequences
   * @param revisionNumber a revision number, if null the current revision will be created
   * @param project        the context project
   * @param isDeleted      if true, the file is deleted
   * @return a created revision
   * @throws com.intellij.openapi.vcs.VcsException
   *          if there is a problem with creating revision
   */
  public static ContentRevision createRevision(VirtualFile vcsRoot,
                                               String path,
                                               VcsRevisionNumber revisionNumber,
                                               Project project,
                                               boolean isDeleted) throws VcsException {
    final String name = vcsRoot.getPath() + "/" + GitUtil.unescapePath(path);
    final FilePath file = isDeleted ? VcsUtil.getFilePathForDeletedFile(name, false) : VcsUtil.getFilePath(name, false);
    if (revisionNumber != null) {
      return new GitContentRevision(file, (GitRevisionNumber)revisionNumber, project);
    }
    else {
      return CurrentContentRevision.create(file);
    }
  }

  public static ContentRevision createRevision(final VirtualFile file, final VcsRevisionNumber revisionNumber, final Project project)
    throws VcsException {
    final FilePathImpl filePath = new FilePathImpl(file);
    if (revisionNumber != null) {
      return new GitContentRevision(filePath, (GitRevisionNumber)revisionNumber, project);
    }
    else {
      return CurrentContentRevision.create(filePath);
    }
  }

  @Override
  public String toString() {
    return myFile.getPath();
  }
}
