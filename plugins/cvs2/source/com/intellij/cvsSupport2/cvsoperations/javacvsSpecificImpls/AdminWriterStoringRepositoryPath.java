package com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls;

import org.netbeans.lib.cvsclient.CvsRoot;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.admin.IAdminWriter;
import org.netbeans.lib.cvsclient.file.*;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.cvsoperations.common.RepositoryPathProvider;
import com.intellij.cvsSupport2.errorHandling.InvalidModuleDescriptionException;

public class AdminWriterStoringRepositoryPath implements IAdminWriter, RepositoryPathProvider {
  private String myRepositoryPath = null;
  private String myModulePath = null;
  private final String myModuleName;
  private final String myCvsRoot;

  public AdminWriterStoringRepositoryPath(String moduleName, String cvsRoot) {
    myModuleName = moduleName;

    myCvsRoot = cvsRoot;
  }

  public void writeTemplateFile(DirectoryObject directoryObject,
                                int fileLength,
                                InputStream inputStream,
                                IReaderFactory readerFactory,
                                IClientEnvironment clientEnvironment) throws IOException {
    CvsUtil.skip(inputStream, fileLength);
  }

  public void editFile(FileObject fileObject, Entry entry, ICvsFileSystem cvsFileSystem, IFileReadOnlyHandler fileReadOnlyHandler) throws IOException {
  }

  public void setStickyTagForDirectory(DirectoryObject directoryObject, String tag, ICvsFileSystem cvsFileSystem) throws IOException {
  }

  public void removeEntryForFile(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) throws IOException {
  }

  public void setEntry(DirectoryObject directoryObject, Entry entry, ICvsFileSystem cvsFileSystem) throws IOException {
  }

  public void ensureCvsDirectory(DirectoryObject directoryObject, String repositoryPath, CvsRoot cvsRoot, ICvsFileSystem cvsFileSystem) throws IOException {
    if (myRepositoryPath == null) {
      myRepositoryPath = repositoryPath;
      myModulePath = cvsFileSystem.getRelativeRepositoryPath(repositoryPath);
    }
  }

  public void setEntriesDotStatic(DirectoryObject directoryObject, boolean set, ICvsFileSystem cvsFileSystem) throws IOException {
  }

  public void uneditFile(FileObject fileObject, ICvsFileSystem cvsFileSystem, IFileReadOnlyHandler fileReadOnlyHandler) throws IOException {
  }

  public void pruneDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
  }

  public void directoryAdded(DirectoryObject directory, ICvsFileSystem cvsFileSystem) throws IOException {
  }

  public String getRepositoryPath(String repository) {
    if (myRepositoryPath == null) throw new InvalidModuleDescriptionException(com.intellij.CvsBundle.message("error.mesage.cannot.expand.module", myModuleName), myCvsRoot);
    return myRepositoryPath;
  }

  public String getModulePath() {
    return myModulePath;
  }
}
