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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.intellij.openapi.vcs.impl.CurrentRevisionProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class VcsCurrentRevisionProxy implements ByteBackedContentRevision {
  @NotNull private final DiffProvider myDiffProvider;
  @NotNull private final VirtualFile myFile;
  @NotNull private final Project myProject;
  @NotNull private final VcsKey myVcsKey;

  @Nullable
  public static VcsCurrentRevisionProxy create(@NotNull VirtualFile file, @NotNull Project project) {
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    if (vcs != null) {
      DiffProvider diffProvider = vcs.getDiffProvider();
      if (diffProvider != null) {
        return new VcsCurrentRevisionProxy(diffProvider, file, project, vcs.getKeyInstanceMethod());
      }
    }
    return null;
  }

  private VcsCurrentRevisionProxy(@NotNull DiffProvider diffProvider,
                                  @NotNull VirtualFile file,
                                  @NotNull Project project,
                                  @NotNull VcsKey vcsKey) {
    myDiffProvider = diffProvider;
    myFile = file;
    myProject = project;
    myVcsKey = vcsKey;
  }

  @Nullable
  public String getContent() throws VcsException {
    return ContentRevisionCache.getAsString(getContentAsBytes(), getFile(), myFile.getCharset());
  }

  @Nullable
  @Override
  public byte[] getContentAsBytes() throws VcsException {
    return getVcsRevision().second;
  }

  @NotNull
  public FilePath getFile() {
    return VcsUtil.getFilePath(myFile);
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    try {
      return getVcsRevision().first;
    }
    catch(VcsException ex) {
      return VcsRevisionNumber.NULL;
    }
  }

  @NotNull
  private Pair<VcsRevisionNumber, byte[]> getVcsRevision() throws VcsException {
    try {
      return ContentRevisionCache.getOrLoadCurrentAsBytes(myProject, getFile(), myVcsKey,
                                                          new CurrentRevisionProvider() {
                                                            @Override
                                                            public VcsRevisionNumber getCurrentRevision() throws VcsException {
                                                              return getCurrentRevisionNumber();
                                                            }

                                                            @Override
                                                            public Pair<VcsRevisionNumber, byte[]> get() throws VcsException {
                                                              return loadContent();
                                                            }
                                                          });
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  @NotNull
  private VcsRevisionNumber getCurrentRevisionNumber() throws VcsException {
    VcsRevisionNumber currentRevision = myDiffProvider.getCurrentRevision(myFile);

    if (currentRevision == null) {
      throw new VcsException("Failed to fetch current revision");
    }

    return currentRevision;
  }

  @NotNull
  private Pair<VcsRevisionNumber, byte[]> loadContent() throws VcsException {
    VcsRevisionNumber currentRevision = getCurrentRevisionNumber();
    ContentRevision contentRevision = myDiffProvider.createFileContent(currentRevision, myFile);

    if (contentRevision == null) {
      throw new VcsException("Failed to create content for current revision");
    }

    byte[] bytes;
    if (contentRevision instanceof ByteBackedContentRevision) {
      bytes = ((ByteBackedContentRevision)contentRevision).getContentAsBytes();
    }
    else {
      String content = contentRevision.getContent();
      if (content == null) throw new VcsException("Can't get revision content");
      bytes = content.getBytes(myFile.getCharset());
    }
    return Pair.create(currentRevision, bytes);
  }
}
