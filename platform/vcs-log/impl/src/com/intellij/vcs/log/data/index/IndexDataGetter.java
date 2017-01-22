/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.StorageException;
import com.intellij.vcs.log.impl.FatalErrorHandler;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

public class IndexDataGetter {
  @NotNull private final Project myProject;
  @NotNull private final Set<VirtualFile> myRoots;
  @NotNull private final VcsLogPersistentIndex.IndexStorage myIndexStorage;
  @NotNull private final FatalErrorHandler myFatalErrorsConsumer;

  public IndexDataGetter(@NotNull Project project,
                         @NotNull Set<VirtualFile> roots,
                         @NotNull VcsLogPersistentIndex.IndexStorage storage,
                         @NotNull FatalErrorHandler fatalErrorsConsumer) {
    myProject = project;
    myRoots = roots;
    myIndexStorage = storage;
    myFatalErrorsConsumer = fatalErrorsConsumer;
  }

  @Nullable
  public String getFullMessage(int index) {
    try {
      return myIndexStorage.messages.get(index);
    }
    catch (IOException e) {
      myFatalErrorsConsumer.consume(this, e);
    }
    return null;
  }

  @NotNull
  public Set<FilePath> getFileNames(@NotNull FilePath path, int commit) {
    VirtualFile root = VcsUtil.getVcsRootFor(myProject, path);
    if (myRoots.contains(root)) {
      try {
        return myIndexStorage.paths.getFileNames(path, commit);
      }
      catch (IOException | StorageException e) {
        myFatalErrorsConsumer.consume(this, e);
      }
    }

    return Collections.emptySet();
  }
}
