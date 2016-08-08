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
package com.intellij.cvsSupport2.checkinProject;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.cvsSupport2.application.CvsInfo;
import org.netbeans.lib.cvsclient.admin.Entry;

import java.util.ArrayList;
import java.util.Collection;

/**
 * author: lesya
 */

public class DirectoryContent {
  private final Collection<VirtualFileEntry> myDirectories = new ArrayList<>();
  private final Collection<Entry> myDeletedDirectories = new ArrayList<>();
  private final Collection<VirtualFile> myUnknownDirectories = new ArrayList<>();
  private final Collection<VirtualFileEntry> myFiles = new ArrayList<>();
  private final Collection<Entry> myDeletedFiles = new ArrayList<>();
  private final Collection<VirtualFile> myUnknownFiles = new ArrayList<>();
  private final CvsInfo myCvsInfo;
  private final Collection<VirtualFile> myIgnoredFiles = new ArrayList<>();

  public DirectoryContent(CvsInfo cvsInfo) {
    myCvsInfo = cvsInfo;
  }

  public void addDirectory(VirtualFileEntry directoryEntry) {
      myDirectories.add(directoryEntry);
  }

  public void addDeletedDirectory(Entry entry) {
    myDeletedDirectories.add(entry);
  }

  public void addUnknownDirectory(VirtualFile directoryFile) {
      myUnknownDirectories.add(directoryFile);
  }

  public void addUnknownFile(VirtualFile file) {
      myUnknownFiles.add(file);
  }

  public void addIgnoredFile(VirtualFile file){
    myIgnoredFiles.add(file);
  }

  public void addDeletedFile(Entry fileName) {
    myDeletedFiles.add(fileName);
  }

  public void addFile(VirtualFileEntry fileEntry) {
      myFiles.add(fileEntry);
  }

  public Collection<VirtualFileEntry> getDirectories() { return myDirectories; }

  public Collection<Entry> getDeletedDirectories() { return myDeletedDirectories; }

  public Collection<VirtualFile> getUnknownDirectories() { return myUnknownDirectories; }

  public Collection<VirtualFileEntry> getFiles() { return myFiles; }

  public Collection<Entry> getDeletedFiles() { return myDeletedFiles; }

  public Collection<VirtualFile> getUnknownFiles() { return myUnknownFiles; }

  public CvsInfo getCvsInfo() {
    return myCvsInfo;
  }

  public Collection<VirtualFile> getIgnoredFiles() {
    return myIgnoredFiles;
  }
}
