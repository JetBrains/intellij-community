/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.wholeTree;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import git4idea.GitBinaryContentRevision;
import git4idea.GitRevisionNumber;

import java.util.List;

/**
 * @author irengrig
 *         Date: 12/20/10
 *         Time: 3:03 PM
 */
public class GitBinaryMultipleContentsRevision extends GitMultipleContentsRevision implements BinaryContentRevision {
  public GitBinaryMultipleContentsRevision(FilePath path, List<GitRevisionNumber> list, GitBinaryContentRevision contentRevision)
    throws VcsException {
    super(path, list, contentRevision);
  }

  @Override
  public byte[] getBinaryContent() throws VcsException {
    return ((GitBinaryContentRevision) myRevision).getBinaryContent();
  }
}
