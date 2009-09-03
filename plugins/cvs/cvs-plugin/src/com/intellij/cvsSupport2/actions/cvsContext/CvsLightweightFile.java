package com.intellij.cvsSupport2.actions.cvsContext;

import java.io.File;

public class CvsLightweightFile {
  private final File myCvsFile;
  private final File myLocalFile;

  public CvsLightweightFile(File cvsFile, File localFile) {
    myCvsFile = cvsFile;
    myLocalFile = localFile;
  }

  public File getCvsFile() {
    return myCvsFile;
  }

  public File getLocalFile() {
    return myLocalFile;
  }

  public File getRoot() {
    if (myLocalFile == null) return null;
    return getRoot(myLocalFile);
  }

  private static File getRoot(File current) {
    while (current != null){
      if (current.isDirectory()) return current;
      current = current.getParentFile();
    }
    return null;
  }

  public String getModuleName() {
    return myCvsFile.getPath().replace(File.separatorChar, '/');
  }

}