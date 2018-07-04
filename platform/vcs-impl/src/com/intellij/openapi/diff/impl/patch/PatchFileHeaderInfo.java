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
package com.intellij.openapi.diff.impl.patch;

import com.intellij.vcs.log.VcsUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class PatchFileHeaderInfo {
  @NotNull private final String myMessage;
  @Nullable private final VcsUser myAuthor;
  @Nullable private final String myBaseRevision;

  PatchFileHeaderInfo(@NotNull String message, @Nullable VcsUser author, @Nullable String revision) {
    myMessage = message;
    myAuthor = author;
    myBaseRevision = revision;
  }

  @NotNull
  public String getMessage() {
    return myMessage;
  }

  @Nullable
  public VcsUser getAuthor() {
    return myAuthor;
  }

  @Nullable
  public String getBaseRevision() {
    return myBaseRevision;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PatchFileHeaderInfo info = (PatchFileHeaderInfo)o;
    return Objects.equals(myMessage, info.myMessage) &&
           Objects.equals(myAuthor, info.myAuthor) &&
           Objects.equals(myBaseRevision, info.myBaseRevision);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myMessage, myAuthor, myBaseRevision);
  }
}