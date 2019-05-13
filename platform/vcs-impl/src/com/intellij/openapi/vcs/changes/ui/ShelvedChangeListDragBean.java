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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFile;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChange;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ShelvedChangeListDragBean {
  @NotNull private final List<ShelvedChange> myShelvedChanges;
  @NotNull private final List<ShelvedBinaryFile> myBinaries;
  @NotNull private final List<ShelvedChangeList> myShelvedChangelists;

  public ShelvedChangeListDragBean(@NotNull List<ShelvedChange> shelvedChanges,
                                   @NotNull List<ShelvedBinaryFile> binaries,
                                   @NotNull List<ShelvedChangeList> shelvedChangelists) {
    myShelvedChanges = shelvedChanges;
    myBinaries = binaries;
    myShelvedChangelists = shelvedChangelists;
  }

  @NotNull
  public List<ShelvedChange> getChanges() {
    return myShelvedChanges;
  }

  @NotNull
  public List<ShelvedBinaryFile> getBinaryFiles() {
    return myBinaries;
  }

  @NotNull
  public List<ShelvedChangeList> getShelvedChangelists() {
    return myShelvedChangelists;
  }
}
