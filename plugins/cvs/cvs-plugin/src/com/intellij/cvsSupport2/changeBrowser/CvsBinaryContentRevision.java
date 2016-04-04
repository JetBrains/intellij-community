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

package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author yole
 */
public class CvsBinaryContentRevision extends CvsContentRevision implements BinaryContentRevision {
  private byte[] myContent;

  public CvsBinaryContentRevision(final File file,
                                  final File localFile,
                                  final RevisionOrDate revision,
                                  final CvsEnvironment environment,
                                  final Project project) {
    super(file, localFile, revision, environment, project);
  }

  @Nullable
  public byte[] getBinaryContent() throws VcsException {
    return getContentAsBytes();
  }

  @Override @NonNls
  public String toString() {
    return "CvsContentRevision:" + myFile + "@" + myRevision;
  }
}