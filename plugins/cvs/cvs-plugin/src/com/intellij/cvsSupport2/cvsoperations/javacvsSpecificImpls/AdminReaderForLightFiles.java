package com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.admin.IAdminReader;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import com.intellij.openapi.util.text.StringUtil;

/**
 * author: lesya
 */
public class AdminReaderForLightFiles implements IAdminReader{
  private final Map<File, Entry> myFileToEntryMap;

  public AdminReaderForLightFiles(Map<File, Entry> fileToEntryMap) {
    myFileToEntryMap = fileToEntryMap;
  }

  public Collection getEntries(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) throws IOException {
    String path = directoryObject.getPath();
    if (StringUtil.startsWithChar(path, '/')) path = path.substring(1);
    return collectEntriesForPath(new File(path));
  }

  private Collection collectEntriesForPath(File parent) {
    HashSet result = new HashSet();
    for (Iterator iterator = myFileToEntryMap.keySet().iterator(); iterator.hasNext();) {
      File file = (File) iterator.next();
      if (file.getParentFile().equals(parent)) result.add(myFileToEntryMap.get(file));
    }
    return result;
  }

  public boolean isModified(FileObject fileObject, Date entryLastModified, ICvsFileSystem cvsFileSystem) {
    return false;
  }

    public boolean isStatic(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
        return false;
    }

    public Entry getEntry(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) throws IOException {
    String path = fileObject.getPath();
    if (StringUtil.startsWithChar(path, '/')) path = path.substring(1);
    return getEntryForPath(new File(path));
  }

  private Entry getEntryForPath(File requested) {
    for (Iterator iterator = myFileToEntryMap.keySet().iterator(); iterator.hasNext();) {
      File file = (File) iterator.next();
      if (file.equals(requested)) return myFileToEntryMap.get(file);
    }
    return null;
  }

  public String getRepositoryForDirectory(DirectoryObject directoryObject, String repository, ICvsFileSystem cvsFileSystem) throws IOException {
    String path = directoryObject.getPath();
    if (StringUtil.startsWithChar(path, '/')) path = path.substring(1);
    return repository + path.replace(File.separatorChar, '/');
  }

  public boolean hasCvsDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    return false;
  }

  public String getStickyTagForDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    return null;
  }

}
