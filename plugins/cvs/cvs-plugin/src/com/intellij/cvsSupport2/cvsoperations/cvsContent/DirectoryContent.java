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
package com.intellij.cvsSupport2.cvsoperations.cvsContent;

import com.intellij.util.containers.HashSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

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
    Collections.sort(result, (o, o1) -> o.compareToIgnoreCase(o1));
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
