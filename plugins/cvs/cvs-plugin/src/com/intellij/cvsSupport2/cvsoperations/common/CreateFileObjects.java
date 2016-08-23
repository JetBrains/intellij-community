/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;

import java.io.File;
import java.util.*;

/**
 * author: lesya
 */
public class CreateFileObjects {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsoperations.common.CreateFileObjects");

  private final File[] myFiles;
  private final String myRootPath;
  private final Map<File, AbstractFileObject> myFileToDirectoryObjectMap = new com.intellij.util.containers.HashMap<>();
  private final Collection<AbstractFileObject> myResult = new ArrayList<>();
  private final Set<File> myCreatedFiles = new HashSet<>();

  public CreateFileObjects(File root, File[] files) {
    String myRootPath = root.getAbsolutePath();
    if (myRootPath.endsWith(File.separator))
      myRootPath = myRootPath.substring(0, myRootPath.length() - 1);
    this.myRootPath = myRootPath;
    myFiles = files;
  }

  public Collection<AbstractFileObject> execute(){
    for (File file : myFiles) {
      if (file.isDirectory() || file.isFile() || file.getParentFile().isDirectory()) {
        String fileAbsolutePath = file.getAbsolutePath();
        String filePath = fileAbsolutePath.equals(myRootPath) ? "/" : fileAbsolutePath.substring(myRootPath.length() + 1);
        File relativeFile = new File(filePath);
        myResult.add(createAbstractFileObject(relativeFile.getParentFile(), relativeFile, file.isDirectory()));
      }
    }

    return myResult;
  }

  private AbstractFileObject createDirectoryObject(File relativeFile){
    return createAbstractFileObject(relativeFile.getParentFile(), relativeFile, true);
  }

  private AbstractFileObject createAbstractFileObject(File parent, File file, boolean isDirectory){
    myCreatedFiles.add(file);
    String relativeFileName = "/" + file.getName();
    if (parent == null){
      return isDirectory ?
        DirectoryObject.createInstance(relativeFileName) : FileObject.createInstance(relativeFileName);
    } else {
      DirectoryObject parentObject = getDirectoryObjectFor(parent);
      return isDirectory ?
        DirectoryObject.createInstance(parentObject, relativeFileName) : FileObject.createInstance(parentObject, relativeFileName);
    }
  }

  private DirectoryObject getDirectoryObjectFor(File parent) {
    if (!myFileToDirectoryObjectMap.containsKey(parent)){
      myFileToDirectoryObjectMap.put(parent, createDirectoryObject(parent));
    }

    return (DirectoryObject) myFileToDirectoryObjectMap.get(parent);
  }
}
