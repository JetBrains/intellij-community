package com.intellij.cvsSupport2.cvsoperations.cvsCheckOut;

import org.netbeans.lib.cvsclient.admin.AdminReader;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.admin.IAdminReader;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

/**
 * author: lesya
 */
public class CheckoutAdminReader implements IAdminReader{
  private final static IAdminReader ourStandardAdminReader = new AdminReader();
  public Entry getEntry(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) throws IOException {
    return null;
  }

  public Collection getEntries(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) throws IOException {
    return new ArrayList();
  }

  public String getRepositoryForDirectory(DirectoryObject directoryObject, String repository, ICvsFileSystem cvsFileSystem) throws IOException {
    return ourStandardAdminReader.getRepositoryForDirectory(directoryObject, repository, cvsFileSystem);
  }

  public String getStickyTagForDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    return null;
  }

  public boolean hasCvsDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    return ourStandardAdminReader.hasCvsDirectory(directoryObject, cvsFileSystem);
  }

  public boolean isModified(FileObject fileObject, Date entryLastModified, ICvsFileSystem cvsFileSystem) {
    return false;
  }

  public boolean isStatic(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    return false;
  }
}
