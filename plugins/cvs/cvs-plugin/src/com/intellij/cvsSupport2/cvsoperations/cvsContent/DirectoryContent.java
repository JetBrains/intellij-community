// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.cvsoperations.cvsContent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public class DirectoryContent {
  private final Collection<String> mySubDirectories = new HashSet<>();
  private final Collection<String> myFiles = new HashSet<>();
  private final Collection<String> myModules = new HashSet<>();

  public int getTotalSize() {
    return mySubDirectories.size() + myFiles.size() + myModules.size();
  }

  public void addSubDirectory(String subDirectoryName){
    mySubDirectories.add(subDirectoryName);
  }

  public void addFile(String fileName){
    myFiles.add(fileName);
  }

  public void addModule(String moduleName){
    myModules.add(moduleName);
  }

  public Collection<String> getSubDirectories() {
    return convertToSortedCollection(mySubDirectories);
  }

  public Collection<String> getFiles() {
    return convertToSortedCollection(myFiles);
  }

  public Collection<String> getSubModules() {
    return convertToSortedCollection(myModules);
  }

  public Collection<String> getSubDirectoriesRaw() {
    return mySubDirectories;
  }

  public Collection<String> getFilesRaw() {
    return myFiles;
  }

  public Collection<String> getSubModulesRaw() {
    return myModules;
  }

  private static Collection<String> convertToSortedCollection(Collection<String> collection) {
    ArrayList<String> result = new ArrayList<>(collection);
    result.sort((o, o1) -> o.compareToIgnoreCase(o1));
    return result;
  }

  public void copyDataFrom(DirectoryContent directoryContent) {
    mySubDirectories.addAll(directoryContent.getSubDirectories());
    myFiles.addAll(directoryContent.getFiles());
    myModules.addAll(directoryContent.getSubModules());
  }

  public void clear() {
    mySubDirectories.clear();
    myFiles.clear();
    myModules.clear();
  }
}
