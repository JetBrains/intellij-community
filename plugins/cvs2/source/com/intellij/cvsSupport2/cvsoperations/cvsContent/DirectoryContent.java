package com.intellij.cvsSupport2.cvsoperations.cvsContent;

import com.intellij.util.containers.HashSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

public class DirectoryContent {
  private Collection mySubDirectories = new HashSet();
  private Collection myFiles = new HashSet();
  private Collection myModules = new HashSet();

  public void addSubDirectory(String subDirectoryName){
    mySubDirectories.add(subDirectoryName);
  }

  public void addFile(String fileName){
    myFiles.add(fileName);
  }

  public void addModule(String moduleName){
    myModules.add(moduleName);
  }

  public Collection getSubDirectories() {
    return convertToSortedCollection(mySubDirectories);
  }

  public Collection getFiles() {
    return convertToSortedCollection(myFiles);
  }

  public Collection getSubModules() {
    return convertToSortedCollection(myModules);
  }

  private Collection convertToSortedCollection(Collection collection) {
    ArrayList result = new ArrayList(collection);
    Collections.sort(result, new Comparator() {
      public int compare(Object o, Object o1) {
        return ((String)o).compareToIgnoreCase((String)o1);
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
