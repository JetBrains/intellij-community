package com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls;

import org.netbeans.lib.cvsclient.admin.IAdminReader;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import java.util.Date;
import java.util.Collection;
import java.io.IOException;

/**
 * author: lesya
 */
public class DeafAdminReader implements IAdminReader{
  public boolean isModified(FileObject fileObject, Date entryLastModified, ICvsFileSystem cvsFileSystem) {
    return false;
  }

  public boolean isStatic(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    return false;
  }

  public Entry getEntry(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) throws IOException {
    return null;
  }

  public boolean hasCvsDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    return false;
  }

  public Collection getEntries(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) throws IOException {
    return null;
  }

  public String getRepositoryForDirectory(DirectoryObject directoryObject, String repository, ICvsFileSystem cvsFileSystem) throws IOException {
    return repository + directoryObject.getPath();
  }

  public String getStickyTagForDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    return null;
  }
}
