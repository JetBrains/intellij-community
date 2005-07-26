package org.netbeans.lib.cvsclient;

import org.netbeans.lib.cvsclient.admin.IAdminReader;
import org.netbeans.lib.cvsclient.admin.IAdminWriter;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.file.*;
import org.netbeans.lib.cvsclient.util.BugLog;
import org.netbeans.lib.cvsclient.util.IIgnoreFileFilter;

import java.io.File;

/**
 * @author  Thomas Singer
 */
public final class ClientEnvironment
    implements IClientEnvironment {

  // Fields =================================================================

  private final IConnection connection;
  private final CvsRoot cvsRoot;
  private final ICvsFileSystem cvsFileSystem;
  private final ILocalFileReader localFileReader;
  private final ILocalFileWriter localFileWriter;
  private final IAdminReader adminReader;
  private final IAdminWriter adminWriter;
  private final IIgnoreFileFilter ignoreFileFilter;
  private final IFileReadOnlyHandler fileReadOnlyHandler;
  private final String charset;
  private final ICvsRootProvider cvsRootProvider;

  // Setup ==================================================================

  public ClientEnvironment(IConnection connection, File localRootDirectory, File adminRootDirectory, CvsRoot cvsRoot,
                           ILocalFileReader localFileReader, ILocalFileWriter localFileWriter,
                           IAdminReader adminReader, IAdminWriter adminWriter,
                           IIgnoreFileFilter ignoreFileFilter, IFileReadOnlyHandler fileReadOnlyHandler,
                           String charset) {
    this(connection, localRootDirectory, adminRootDirectory, cvsRoot,localFileReader, localFileWriter ,adminReader, adminWriter,
        ignoreFileFilter, fileReadOnlyHandler, charset, ICvsRootProvider.DUMMY);
  }

  public ClientEnvironment(IConnection connection, File localRootDirectory, File adminRootDirectory, CvsRoot cvsRoot,
                           ILocalFileReader localFileReader, ILocalFileWriter localFileWriter,
                           IAdminReader adminReader, IAdminWriter adminWriter,
                           IIgnoreFileFilter ignoreFileFilter, IFileReadOnlyHandler fileReadOnlyHandler,
                           String charset, ICvsRootProvider cvsRootProvider) {
    BugLog.getInstance().assertNotNull(connection);
    BugLog.getInstance().assertNotNull(localRootDirectory);
    BugLog.getInstance().assertNotNull(adminRootDirectory);
    BugLog.getInstance().assertNotNull(cvsRoot);
    BugLog.getInstance().assertNotNull(localFileReader);
    BugLog.getInstance().assertNotNull(localFileWriter);
    BugLog.getInstance().assertNotNull(adminReader);
    BugLog.getInstance().assertNotNull(adminWriter);
    BugLog.getInstance().assertNotNull(ignoreFileFilter);
    BugLog.getInstance().assertNotNull(fileReadOnlyHandler);
    BugLog.getInstance().assertNotNull(cvsRootProvider);

    this.connection = connection;
    this.cvsRoot = cvsRoot;
    this.cvsFileSystem = new CvsFileSystem(localRootDirectory, adminRootDirectory, connection.getRepository());
    this.localFileReader = localFileReader;
    this.localFileWriter = localFileWriter;
    this.adminReader = adminReader;
    this.adminWriter = adminWriter;
    this.ignoreFileFilter = ignoreFileFilter;
    this.fileReadOnlyHandler = fileReadOnlyHandler;
    this.charset = charset;
    this.cvsRootProvider = cvsRootProvider;
  }

  // Implemented ============================================================

  public IConnection getConnection() {
    return connection;
  }

  public ICvsFileSystem getCvsFileSystem() {
    return cvsFileSystem;
  }

  public CvsRoot getCvsRoot() {
    return cvsRoot;
  }

  public IAdminReader getAdminReader() {
    return adminReader;
  }

  public IAdminWriter getAdminWriter() {
    return adminWriter;
  }

  public ILocalFileReader getLocalFileReader() {
    return localFileReader;
  }

  public ILocalFileWriter getLocalFileWriter() {
    return localFileWriter;
  }

  public IIgnoreFileFilter getIgnoreFileFilter() {
    return ignoreFileFilter;
  }

  public IFileReadOnlyHandler getFileReadOnlyHandler() {
    return fileReadOnlyHandler;
  }

  public String getCharset() {
    return charset;
  }

  public IClientEnvironment createEnvironmentForDirectory(DirectoryObject directory) {
      IConnection connection = cvsRootProvider.getConnection(directory, cvsFileSystem);
      if (connection == null) return null;
      CvsRoot cvsRoot = cvsRootProvider.getCvsRoot(directory, cvsFileSystem);
      if (cvsRoot == null) return null;
      return new ClientEnvironment(connection, cvsFileSystem.getLocalFileSystem().getFile(directory),
          cvsFileSystem.getAdminFileSystem().getFile(directory),
          cvsRoot
          , getLocalFileReader(), getLocalFileWriter(), getAdminReader(), getAdminWriter(),
          getIgnoreFileFilter(), getFileReadOnlyHandler(), getCharset(), cvsRootProvider);

  }

}
