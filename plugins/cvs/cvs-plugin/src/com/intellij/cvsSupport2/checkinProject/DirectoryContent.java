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
  private final Collection<VirtualFileEntry> myDirectories = new ArrayList<VirtualFileEntry>();
  private final Collection<Entry> myDeletedDirectories = new ArrayList<Entry>();
  private final Collection<VirtualFile> myUnknownDirectories = new ArrayList<VirtualFile>();
  private final Collection<VirtualFileEntry> myFiles = new ArrayList<VirtualFileEntry>();
  private final Collection<Entry> myDeletedFiles = new ArrayList<Entry>();
  private final Collection<VirtualFile> myUnknownFiles = new ArrayList<VirtualFile>();
  private final CvsInfo myCvsInfo;
  private final Collection<VirtualFile> myIgnoredFiles = new ArrayList<VirtualFile>();

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
