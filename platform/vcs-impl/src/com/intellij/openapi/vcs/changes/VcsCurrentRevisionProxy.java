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
import java.nio.charset.Charset;

/**
 * @author yole
 */
public class VcsCurrentRevisionProxy implements ContentRevision {
  private final DiffProvider myDiffProvider;
  private final VirtualFile myFile;
  private final Project myProject;
  private final VcsKey myVcsKey;

  @Nullable
  public static VcsCurrentRevisionProxy create(final VirtualFile file, final Project project, final VcsKey vcsKey) {
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).findVcsByName(vcsKey.getName());
    if (vcs != null) {
      final DiffProvider diffProvider = vcs.getDiffProvider();
      if (diffProvider != null) {
        return new VcsCurrentRevisionProxy(diffProvider, file, project, vcsKey);
      }
    }
    return null;
  }

  private VcsCurrentRevisionProxy(final DiffProvider diffProvider, final VirtualFile file, final Project project, final VcsKey vcsKey) {
    myDiffProvider = diffProvider;
    myFile = file;
    myProject = project;
    myVcsKey = vcsKey;
  }

  @Nullable
  public String getContent() throws VcsException {
    return getVcsRevision().getContent();
  }

  @NotNull
  public FilePath getFile() {
    return VcsUtil.getFilePath(myFile);
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    try {
      return getVcsRevision().getRevisionNumber();
    }
    catch(VcsException ex) {
      return VcsRevisionNumber.NULL;
    }
  }

  private ContentRevision getVcsRevision() throws VcsException {
    final FilePath file = getFile();
    final Pair<VcsRevisionNumber, byte[]> pair;
    try {
      pair = ContentRevisionCache.getOrLoadCurrentAsBytes(myProject, file, myVcsKey,
                                                          new CurrentRevisionProvider() {
                                                             @Override
                                                             public VcsRevisionNumber getCurrentRevision() throws VcsException {
                                                               return getCurrentRevisionNumber();
                                                             }

                                                             @Override
                                                             public Pair<VcsRevisionNumber, byte[]> get() throws VcsException, IOException {
                                                               return loadContent();
                                                             }
                                                           });
    }
    catch (IOException e) {
      throw new VcsException(e);
    }

    return new ByteBackedContentRevision() {
      @Override
      public String getContent() throws VcsException {
        byte[] byteContent = getContentAsBytes();
        return ContentRevisionCache.getAsString(byteContent, file, null);
      }

      @Nullable
      @Override
      public byte[] getContentAsBytes() throws VcsException {
        return pair.getSecond();
      }

      @NotNull
      @Override
      public FilePath getFile() {
        return file;
      }

      @NotNull
      @Override
      public VcsRevisionNumber getRevisionNumber() {
        return pair.getFirst();
      }
    };
  }

  private VcsRevisionNumber getCurrentRevisionNumber() throws VcsException {
    final VcsRevisionNumber currentRevision = myDiffProvider.getCurrentRevision(myFile);
    if (currentRevision == null) {
      throw new VcsException("Failed to fetch current revision");
    }
    return currentRevision;
  }

  private Pair<VcsRevisionNumber, byte[]> loadContent() throws VcsException {
    final VcsRevisionNumber currentRevision = getCurrentRevisionNumber();
    final ContentRevision contentRevision = myDiffProvider.createFileContent(currentRevision, myFile);
    if (contentRevision == null) {
      throw new VcsException("Failed to create content for current revision");
    }
    Charset charset = myFile.getCharset();
    return Pair.create(currentRevision, contentRevision.getContent().getBytes(charset));
  }
}
