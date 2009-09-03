package com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls;

import org.netbeans.lib.cvsclient.admin.IAdminReader;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.FileObject;

import java.util.Collection;
import java.util.Date;
import java.util.ArrayList;
import java.io.IOException;

import com.intellij.cvsSupport2.cvsoperations.common.RepositoryPathProvider;

/**
 * author: lesya
 */
public class AdminReaderOnStoredRepositoryPath implements IAdminReader {
  private final RepositoryPathProvider myRepositoryPathProvider;

  public AdminReaderOnStoredRepositoryPath(RepositoryPathProvider adminWriter) {
    myRepositoryPathProvider = adminWriter;
  }

  public Collection getEntries(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) throws IOException {
    return new ArrayList();
  }

  public Entry getEntry(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) throws IOException {
    return null;
  }

  public boolean isModified(FileObject fileObject, Date entryLastModified, ICvsFileSystem cvsFileSystem) {
    return false;
  }

  public boolean isStatic(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    return false;
  }

  public String getRepositoryForDirectory(DirectoryObject directoryObject, String repository, ICvsFileSystem cvsFileSystem)
    throws IOException {
    return myRepositoryPathProvider.getRepositoryPath(repository);
  }

  public boolean hasCvsDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    return true;
  }

  public String getStickyTagForDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    return null;
  }
}
