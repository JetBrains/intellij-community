package com.intellij.cvsSupport2.cvsoperations.common;

import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;

import java.io.File;
import java.util.*;

import com.intellij.openapi.diagnostic.Logger;

/**
 * author: lesya
 */
public class CreateFileObjects {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsoperations.common.CreateFileObjects");

  private final File[] myFiles;
  private String myRootPath;
  private final Map myFileToDirectoryObjectMap = new com.intellij.util.containers.HashMap();
  private final Collection myResult = new ArrayList();
  private final Set myCreatedFiles = new HashSet();

  public CreateFileObjects(File root, File[] files) {
    myRootPath = root.getAbsolutePath();
    if (myRootPath.endsWith(File.separator))
      myRootPath = myRootPath.substring(0, myRootPath.length() - 1);
    myFiles = files;
  }

  public Collection execute(){
    for (int i = 0; i < myFiles.length; i++) {
      File file = myFiles[i];
      LOG.assertTrue(file.isDirectory() || file.isFile() || file.getParentFile().isDirectory(), file.getAbsolutePath());
      String fileAbsolutePath = file.getAbsolutePath();
      String filePath = fileAbsolutePath.equals(myRootPath) ? "/" : fileAbsolutePath.substring(myRootPath.length() + 1);
      File relativeFile =  new File(filePath);
      myResult.add(createAbstractFileObject(relativeFile.getParentFile(), relativeFile, file.isDirectory()));
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
        DirectoryObject.createInstance(relativeFileName) : (AbstractFileObject)FileObject.createInstance(relativeFileName);
    } else {
      DirectoryObject parentObject = getDirectoryObjectFor(parent);
      return isDirectory ?
        DirectoryObject.createInstance(parentObject, relativeFileName) :
          (AbstractFileObject)FileObject.createInstance(parentObject, relativeFileName);
    }
  }

  private DirectoryObject getDirectoryObjectFor(File parent) {
    if (!myFileToDirectoryObjectMap.containsKey(parent)){
      myFileToDirectoryObjectMap.put(parent, createDirectoryObject(parent));
    }

    return (DirectoryObject) myFileToDirectoryObjectMap.get(parent);
  }
}
