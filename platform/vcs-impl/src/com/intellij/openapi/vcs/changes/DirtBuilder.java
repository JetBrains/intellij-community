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

import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsRoot;

import java.util.ArrayList;
import java.util.List;

public class DirtBuilder {
  private final VcsGuess myGuess;

  private final List<FilePathUnderVcs> myFiles;
  private final List<FilePathUnderVcs> myDirs;
  private boolean myEverythingDirty;

  public DirtBuilder(final VcsGuess guess) {
    myGuess = guess;
    myDirs = new ArrayList<FilePathUnderVcs>();
    myFiles = new ArrayList<FilePathUnderVcs>();
    myEverythingDirty = false;
  }

  public DirtBuilder(final DirtBuilder builder) {
    myGuess = builder.myGuess;
    myDirs = new ArrayList<FilePathUnderVcs>(builder.myDirs);
    myFiles = new ArrayList<FilePathUnderVcs>(builder.myFiles);
    myEverythingDirty = builder.myEverythingDirty;
  }

  public void reset() {
    myFiles.clear();
    myDirs.clear();
    myEverythingDirty = false;
  }

  public void everythingDirty() {
    myEverythingDirty = true;
  }

  public void addDirtyFile(final VcsRoot root) {
    myFiles.add(new FilePathUnderVcs(new FilePathImpl(root.path), root.vcs));
  }

  public void addDirtyDirRecursively(final VcsRoot root) {
    myDirs.add(new FilePathUnderVcs(new FilePathImpl(root.path), root.vcs));
  }

  public void addDirtyFile(final FilePathUnderVcs root) {
    myFiles.add(root);
  }

  public void addDirtyDirRecursively(final FilePathUnderVcs root) {
    myDirs.add(root);
  }

  public boolean isEverythingDirty() {
    return myEverythingDirty;
  }

  public List<FilePathUnderVcs> getFilesForVcs() {
    return myFiles;
  }

  public List<FilePathUnderVcs> getDirsForVcs() {
    return myDirs;
  }

  public boolean isEmpty() {
    return myFiles.isEmpty() && myDirs.isEmpty();
  }
}
