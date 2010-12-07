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

import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class LocallyDeletedChange {
  private final String myPresentableUrl;
  private final FilePath myPath;

  public LocallyDeletedChange(@NotNull final FilePath path) {
    myPath = path;
    myPresentableUrl = myPath.getPresentableUrl();
  }

  public FilePath getPath() {
    return myPath;
  }

  @Nullable
  public Icon getAddIcon() {
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LocallyDeletedChange that = (LocallyDeletedChange)o;

    if (!myPresentableUrl.equals(that.myPresentableUrl)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myPresentableUrl.hashCode();
  }

  public String getPresentableUrl() {
    return myPresentableUrl;
  }

  @Nullable
  public String getDescription() {
    return null;
  }

  public String toString() {
    return myPath.getPath();
  }
}
