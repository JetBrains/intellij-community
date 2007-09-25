package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls.AdminReaderForLightFiles;
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
  private final Map<File, Entry> myFileToEntryMap = new com.intellij.util.containers.HashMap<File, Entry>();
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
    myFileToEntryMap.put(file, Entry.createEntryForLine("/" + file.getName() + "/" + myRevision + "/"
        + Entry.getLastModifiedDateFormatter().format(new Date()) + "//"));
  }

  public void addFilesTo(AbstractCommand command){
    AbstractFileObject[] fileObjects = createFileObjects();
    for (AbstractFileObject fileObject : fileObjects) {
      command.getFileObjects().addFileObject(fileObject);
    }
  }

  private AbstractFileObject[] createFileObjects() {
    ArrayList<AbstractFileObject> result = new ArrayList<AbstractFileObject>();
    Collection<File> parents = collectAllParents();
    Map<File, DirectoryObject> parentsMap = new com.intellij.util.containers.HashMap<File, DirectoryObject>();

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
    HashSet<File> result = new HashSet<File>();
    for (final File file : myFileToEntryMap.keySet()) {
      File parentFile = file.getParentFile();
      LOG.assertTrue(parentFile != null, file.getPath());
      result.add(parentFile);
    }
    return result;
  }



}
