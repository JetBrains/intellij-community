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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Throwable2Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
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

  public VcsCurrentRevisionProxy(final DiffProvider diffProvider, final VirtualFile file, final Project project, final VcsKey vcsKey) {
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
    return new FilePathImpl(myFile);
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
    final Pair<VcsRevisionNumber, String> pair;
    try {
      pair = ContentRevisionCache.getOrLoadCurrentAsString(myProject, file, myVcsKey,
                                                           new Throwable2Computable<Pair<VcsRevisionNumber, byte[]>, VcsException, IOException>() {
                                                             @Override
                                                             public Pair<VcsRevisionNumber, byte[]> compute()
                                                               throws VcsException, IOException {
                                                               return loadContent();
                                                             }
                                                           });
    }
    catch (IOException e) {
      throw new VcsException(e);
    }

    return new ContentRevision() {
      @Override
      public String getContent() throws VcsException {
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

  private Pair<VcsRevisionNumber, byte[]> loadContent() throws VcsException {
    final VcsRevisionNumber currentRevision = myDiffProvider.getCurrentRevision(myFile);
    if (currentRevision == null) {
      throw new VcsException("Failed to fetch current revision");
    }
    final ContentRevision contentRevision = myDiffProvider.createFileContent(currentRevision, myFile);
    if (contentRevision == null) {
      throw new VcsException("Failed to create content for current revision");
    }
    Charset charset = myFile.getCharset();
    charset = charset == null ? EncodingManager.getInstance().getDefaultCharset() : charset;
    return new Pair<VcsRevisionNumber, byte[]>(currentRevision, contentRevision.getContent().getBytes(charset));
  }
}
