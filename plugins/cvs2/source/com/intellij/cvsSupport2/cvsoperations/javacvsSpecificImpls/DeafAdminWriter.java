package com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls;

import com.intellij.cvsSupport2.CvsUtil;
import org.netbeans.lib.cvsclient.CvsRoot;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.admin.IAdminWriter;
import org.netbeans.lib.cvsclient.file.*;

import java.io.IOException;
import java.io.InputStream;

/**
 * author: lesya
 */
public class DeafAdminWriter implements IAdminWriter{
  public void ensureCvsDirectory(DirectoryObject directoryObject, String repositoryPath, CvsRoot cvsRoot, ICvsFileSystem cvsFileSystem) throws IOException {

  }

  public void setEntry(DirectoryObject directoryObject, Entry entry, ICvsFileSystem cvsFileSystem) throws IOException {

  }

  public void removeEntryForFile(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) throws IOException {

  }

  public void pruneDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {

  }

  public void editFile(FileObject fileObject, Entry entry, ICvsFileSystem cvsFileSystem, IFileReadOnlyHandler fileReadOnlyHandler) throws IOException {

  }

  public void uneditFile(FileObject fileObject, ICvsFileSystem cvsFileSystem, IFileReadOnlyHandler fileReadOnlyHandler) throws IOException {

  }

  public void setStickyTagForDirectory(DirectoryObject directoryObject, String tag, ICvsFileSystem cvsFileSystem) throws IOException {

  }

  public void setEntriesDotStatic(DirectoryObject directoryObject, boolean set, ICvsFileSystem cvsFileSystem) throws IOException {

  }

  public void writeTemplateFile(DirectoryObject directoryObject, int fileLength, InputStream inputStream, IReaderFactory readerFactory, IClientEnvironment clientEnvironment) throws IOException {
    CvsUtil.skip(inputStream, fileLength);
  }

  public void directoryAdded(DirectoryObject directory, ICvsFileSystem cvsFileSystem) throws IOException {

  }
}
