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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls.AdminReaderForLightFiles;
import com.intellij.util.containers.HashMap;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.admin.IAdminReader;
import org.netbeans.lib.cvsclient.command.AbstractCommand;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;

import java.io.File;
import java.util.*;

/**
 * @author lesya
 */
public class LocalPathIndifferentOperationHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsoperations.common.LocalPathIndifferentOperationHelper");
  private final Map<File, Entry> myFileToEntryMap = new HashMap<>();
  private final IAdminReader myAdminReader = new AdminReaderForLightFiles(myFileToEntryMap);
  private final String myRevision;

  public LocalPathIndifferentOperationHelper(String revision) {
    myRevision = revision;
  }

  public LocalPathIndifferentOperationHelper() {
    this("");
  }

  public IAdminReader getAdminReader() {
    return myAdminReader;
  }

  public void addFile(File file) {
    assert file.getParentFile() != null;
    myFileToEntryMap.put(file, Entry.createEntryForLine("/" + file.getName() + "/" + myRevision + "/"
        + Entry.getLastModifiedDateFormatter().format(new Date()) + "//"));
  }

  public void addFilesTo(AbstractCommand command){
    AbstractFileObject[] fileObjects = createFileObjects();
    for (AbstractFileObject fileObject : fileObjects) {
      command.addFileObject(fileObject);
    }
  }

  private AbstractFileObject[] createFileObjects() {
    ArrayList<AbstractFileObject> result = new ArrayList<>();
    Collection<File> parents = collectAllParents();
    Map<File, DirectoryObject> parentsMap = new HashMap<>();

    for (final File file : parents) {
      String relativeFileName = file.getPath().replace(File.separatorChar, '/');
      if (!StringUtil.startsWithChar(relativeFileName, '/')) relativeFileName = "/" + relativeFileName;
      parentsMap.put(file, DirectoryObject.createInstance(relativeFileName));
    }

    for (final File file: myFileToEntryMap.keySet()) {
      result.add(FileObject.createInstance(parentsMap.get(file.getParentFile()), "/" + file.getName()));
    }

    return result.toArray(new AbstractFileObject[result.size()]);
  }

  private Collection<File> collectAllParents() {
    HashSet<File> result = new HashSet<>();
    for (final File file : myFileToEntryMap.keySet()) {
      File parentFile = file.getParentFile();
      LOG.assertTrue(parentFile != null, file.getPath());
      result.add(parentFile);
    }
    return result;
  }
}
