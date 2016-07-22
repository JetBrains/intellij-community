/*
 *                 Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.sun.com/
 *
 * The Original Code is NetBeans. The Initial Developer of the Original
 * Code is Sun Microsystems, Inc. Portions Copyright 1997-2000 Sun
 * Microsystems, Inc. All Rights Reserved.
 */
package org.netbeans.lib.cvsclient.command;

import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Thomas Singer
 */
final class LocalFiles {

  // Fields =================================================================

  private final Collection<String> fileNames = new ArrayList<>();

  // Setup ==================================================================

  public LocalFiles(DirectoryObject directoryObject, IClientEnvironment clientEnvironment) {
    BugLog.getInstance().assertNotNull(directoryObject);
    BugLog.getInstance().assertNotNull(clientEnvironment);

    clientEnvironment.getLocalFileReader().listFilesAndDirectories(directoryObject, fileNames, null, clientEnvironment.getCvsFileSystem());
  }

  // Accessing ==============================================================

  public void removeFile(String fileName) {
    fileNames.remove(fileName);
  }

  public Collection<String> getFileNames() {
    return fileNames;
  }
}
