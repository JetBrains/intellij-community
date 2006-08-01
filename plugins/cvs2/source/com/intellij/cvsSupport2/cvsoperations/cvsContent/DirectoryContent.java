package com.intellij.cvsSupport2.cvsoperations.cvsContent;

import com.intellij.util.containers.HashSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

public class DirectoryContent {
  private Collection<String> mySubDirectories = new HashSet<String>();
  private Collection<String> myFiles = new HashSet<String>();
  private Collection<String> myModules = new HashSet<String>();

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

  private static Collection<String> convertToSortedCollection(Collection<String> collection) {
    ArrayList<String> result = new ArrayList<String>(collection);
    Collections.sort(result, new Comparator<String>() {
      public int compare(String o, String o1) {
        return o.compareToIgnoreCase(o1);
      }
    });
    return result;
  }

  public void copyDataFrom(DirectoryContent directoryContent) {
    mySubDirectories.addAll(directoryContent.getSubDirectories());
    myFiles.addAll(directoryContent.getFiles());
    myModules.addAll(directoryContent.getSubModules());
  }
}
