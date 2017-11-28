
/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.testFramework.vcs;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class MockContentRevision implements ContentRevision {
  private final FilePath myPath;
  private final VcsRevisionNumber myRevisionNumber;

  public MockContentRevision(final FilePath path, final VcsRevisionNumber revisionNumber) {
    myPath = path;
    myRevisionNumber = revisionNumber;
  }

  @Override
  @Nullable
  public String getContent() {
    return null;
  }

  @Override
  @NotNull
  public FilePath getFile() {
    return myPath;
  }

  @Override
  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return myRevisionNumber;
  }

  @Override
  public String toString() {
    return myPath.getName() + ":" + myRevisionNumber;
  }
}
