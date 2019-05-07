/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SimpleBinaryContentRevision implements BinaryContentRevision {
  @NotNull private final FilePath myPath;
  @NotNull private final String myRevisionPresentationName;


  public SimpleBinaryContentRevision(@NotNull FilePath path) {
    myPath = path;
    myRevisionPresentationName = VcsBundle.message("patched.version.name");
  }


  public SimpleBinaryContentRevision(@NotNull FilePath path, @NotNull String presentationName) {
    myPath = path;
    myRevisionPresentationName = presentationName;
  }

  @Nullable
  @Override
  public String getContent() {
    throw new IllegalStateException();
  }

  @NotNull
  @Override
  public FilePath getFile() {
    return myPath;
  }

  @NotNull
  @Override
  public VcsRevisionNumber getRevisionNumber() {
    return new VcsRevisionNumber() {
      @Override
      public String asString() {
        return myRevisionPresentationName;
      }

      @Override
      public int compareTo(VcsRevisionNumber o) {
        return -1;
      }
    };
  }
}
