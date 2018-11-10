package org.netbeans.lib.cvsclient;

import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

/**
 * author: lesya
 */
public interface ICvsRootProvider {

  ICvsRootProvider DUMMY = new ICvsRootProvider(){
    @Override
    public IConnection getConnection(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
      return null;
    }

    @Override
    public CvsRoot getCvsRoot(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
      return null;
    }
  };

  IConnection getConnection(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem);
  CvsRoot getCvsRoot(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem);
}
