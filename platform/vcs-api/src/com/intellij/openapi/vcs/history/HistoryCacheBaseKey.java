/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsKey;
import org.jetbrains.annotations.NotNull;

public class HistoryCacheBaseKey {
  private final FilePath myFilePath;
  private final VcsKey myVcsKey;

  public HistoryCacheBaseKey(@NotNull FilePath filePath, @NotNull VcsKey vcsKey) {
    myFilePath = filePath;
    myVcsKey = vcsKey;
  }

  @NotNull
  public FilePath getFilePath() {
    return myFilePath;
  }

  @NotNull
  public VcsKey getVcsKey() {
    return myVcsKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HistoryCacheBaseKey baseKey = (HistoryCacheBaseKey)o;

    if (!myFilePath.equals(baseKey.myFilePath)) return false;
    if (!myVcsKey.equals(baseKey.myVcsKey)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myFilePath.hashCode();
    result = 31 * result + myVcsKey.hashCode();
    return result;
  }
}
