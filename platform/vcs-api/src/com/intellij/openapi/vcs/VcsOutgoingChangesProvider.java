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
package com.intellij.openapi.vcs;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public interface VcsOutgoingChangesProvider <T extends CommittedChangeList> extends VcsProviderMarker {
  Pair<VcsRevisionNumber, List<T>> getOutgoingChanges(final VirtualFile vcsRoot, final boolean findRemote) throws VcsException;
  @Nullable
  VcsRevisionNumber getMergeBaseNumber(final VirtualFile anyFileUnderRoot) throws VcsException;
  @NotNull
  <U> Collection<U> whichAreOutgoingChanges(final Collection<U> revisions,
                                            final Convertor<U, VcsRevisionNumber> convertor,
                                            Convertor<U, FilePath> filePatchConvertor,
                                            final VirtualFile vcsRoot) throws VcsException;

  Date getRevisionDate(final VcsRevisionNumber revision);
}
