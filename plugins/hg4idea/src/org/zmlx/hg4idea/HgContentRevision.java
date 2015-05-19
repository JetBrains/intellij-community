// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea;

import com.google.common.base.Objects;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Throwable2Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.command.HgCatCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.IOException;

public class HgContentRevision implements ContentRevision {

  private final Project myProject;
  @NotNull private final HgFile myHgFile;
  @NotNull private final HgRevisionNumber myRevisionNumber;

  private FilePath filePath;

  protected HgContentRevision(Project project, @NotNull HgFile hgFile, @NotNull HgRevisionNumber revisionNumber) {
    myProject = project;
    myHgFile = hgFile;
    myRevisionNumber = revisionNumber;
  }

  @NotNull
  public static HgContentRevision create(Project project, @NotNull HgFile hgFile, @NotNull HgRevisionNumber revisionNumber) {
    return !hgFile.toFilePath().getFileType().isBinary()
           ? new HgContentRevision(project, hgFile, revisionNumber)
           : new HgBinaryContentRevision(project, hgFile, revisionNumber);
  }

  @Nullable
  @Override
  public String getContent() throws VcsException {
    if (myRevisionNumber.isWorkingVersion()) return VcsUtil.getFileContent(myHgFile.getFile().getPath());

    final HgFile fileToCat = HgUtil.getFileNameInTargetRevision(myProject, myRevisionNumber, myHgFile);
    FilePath filePath = fileToCat.toFilePath();
    try {
      return ContentRevisionCache
        .getOrLoadAsString(myProject, filePath, myRevisionNumber, HgVcs.getKey(), ContentRevisionCache.UniqueType.REPOSITORY_CONTENT,
                           new Throwable2Computable<byte[], VcsException, IOException>() {
                             @Override
                             public byte[] compute() throws VcsException, IOException {
                               return loadContent(fileToCat);
                             }
                           }, filePath.getCharset());
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  @NotNull
  private byte[] loadContent(@NotNull HgFile fileToCat) {
    HgCommandResult result = new HgCatCommand(myProject).execute(fileToCat, myRevisionNumber, getFile().getCharset());
    return result != null && result.getExitValue() == 0 ? result.getBytesOutput() : new byte[0];
  }


  @Nullable
  public byte[] getContentAsBytes() throws VcsException {
    final HgFile fileToCat = HgUtil.getFileNameInTargetRevision(myProject, myRevisionNumber, myHgFile);
    try {
      return ContentRevisionCache
        .getOrLoadAsBytes(myProject, VcsUtil.getFilePath(fileToCat.getFile()), myRevisionNumber, HgVcs.getKey(),
                          ContentRevisionCache.UniqueType.REPOSITORY_CONTENT,
                          new Throwable2Computable<byte[], VcsException, IOException>() {
                            @Override
                            public byte[] compute() throws VcsException, IOException {
                              return loadContent(fileToCat);
                            }
                          });
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  @NotNull
  public FilePath getFile() {
    if (filePath == null) {
      filePath = myHgFile.toFilePath();
    }
    return filePath;
  }

  @NotNull
  public HgRevisionNumber getRevisionNumber() {
    return myRevisionNumber;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    HgContentRevision revision = (HgContentRevision)o;

    if (!myHgFile.equals(revision.myHgFile)) {
      return false;
    }
    if (!myRevisionNumber.equals(revision.myRevisionNumber)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myHgFile, myRevisionNumber);
  }
}
