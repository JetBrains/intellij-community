/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import org.netbeans.lib.cvsclient.admin.IAdminReader;
import org.netbeans.lib.cvsclient.command.AbstractCommand;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public abstract class CvsOperationOnFiles extends CvsCommandOperation {
  protected Collection<File> myFiles = new ArrayList<>();
  private Map<CvsRootProvider, ArrayList<File>> myRootsToFiles;

  public CvsOperationOnFiles(IAdminReader reader) {
    super(reader);
  }

  public CvsOperationOnFiles() {
  }

  public synchronized void execute(CvsExecutionEnvironment executionEnvironment, boolean underReadAction) throws VcsException, CommandAbortedException {
    synchronized (CvsOperation.class) {
      if (!myFiles.isEmpty()) {
        try {
          super.execute(executionEnvironment, underReadAction);
        } finally {
          clearCachedEntriesForProcessedFiles();
        }
      }
    }
  }

  private void clearCachedEntriesForProcessedFiles() {
    final CvsEntriesManager entriesManager = CvsEntriesManager.getInstance();
    for (final File myFile : myFiles) {
      final File parentFile = myFile.getParentFile();
      if (parentFile != null) {
        try {
          VirtualFile vParent = CvsVfsUtil.findFileByPath(parentFile.getCanonicalPath().replace(File.separatorChar, '/'));
          if (vParent != null) {
            entriesManager.clearCachedEntriesFor(vParent);
          }
        }
        catch (IOException error) {
          LOG.error(error);
        }
      }
    }
  }

  protected File[] getFilesAsArray(CvsRootProvider root) {
    try {
      Collection<File> files = getRootsToFilesMap().get(root);
      return files.toArray(new File[files.size()]);
    } catch (CannotFindCvsRootException e) {
      LOG.error(e);
      return new File[0];
    }
  }


  public boolean addFile(VirtualFile file) {
    return addFile(file == null ? "" : file.getPath());
  }

  public boolean addFile(File file) {
    myRootsToFiles = null;
    return addFile(file.getAbsolutePath());
  }

  public boolean addFiles(VirtualFile[] file) {
    for (VirtualFile aFile : file) {
      addFile(aFile);
    }
    return true;
  }

  public boolean addFile(String path) {
    return myFiles.add(new File(path));
  }

  public int getFilesCount(){
    return myFiles.size();
  }

  public int getFilesToProcessCount() {
    return getFilesCount();
  }

  private Map<CvsRootProvider, ArrayList<File>> buildRootsToFilesMap() throws CannotFindCvsRootException {
    HashMap<CvsRootProvider,ArrayList<File>> result = new HashMap<>();
    for (File file : myFiles) {
      CvsRootProvider cvsRoot = CvsRootProvider.createOn(file);
      if (cvsRoot == null) {
        throw new CannotFindCvsRootException(file);
      }
      else {
        if (!result.containsKey(cvsRoot)) result.put(cvsRoot, new ArrayList<>());
        (result.get(cvsRoot)).add(file);
      }
    }
    return result;
  }

  protected Map<CvsRootProvider, ArrayList<File>> getRootsToFilesMap() throws CannotFindCvsRootException {
    if (myRootsToFiles == null)
      myRootsToFiles = buildRootsToFilesMap();
    return myRootsToFiles;
  }

  protected Collection<CvsRootProvider> getAllCvsRoots() throws CannotFindCvsRootException {
    return getRootsToFilesMap().keySet();
  }

  protected void addFilesToCommand(CvsRootProvider root, AbstractCommand command) {
    CreateFileObjects createFileObjects = new CreateFileObjects(getLocalRootFor(root), getFilesAsArray(root));
    Collection<AbstractFileObject> fileObjects = createFileObjects.execute();
    for (final AbstractFileObject fileObject : fileObjects) {
      command.addFileObject(fileObject);
    }
  }
}
