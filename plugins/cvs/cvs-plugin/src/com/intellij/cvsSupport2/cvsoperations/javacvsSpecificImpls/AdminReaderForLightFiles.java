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
package com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls;

import com.intellij.openapi.util.text.StringUtil;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.admin.IAdminReader;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

import java.io.File;
import java.util.*;

/**
 * author: lesya
 */
public class AdminReaderForLightFiles implements IAdminReader{
  private final Map<File, Entry> myFileToEntryMap;

  public AdminReaderForLightFiles(Map<File, Entry> fileToEntryMap) {
    myFileToEntryMap = fileToEntryMap;
  }

  @Override
  public Collection getEntries(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    String path = directoryObject.getPath();
    if (StringUtil.startsWithChar(path, '/')) path = path.substring(1);
    return collectEntriesForPath(new File(path));
  }

  private Collection collectEntriesForPath(File parent) {
    HashSet<Entry> result = new HashSet<>();
    for (File file : myFileToEntryMap.keySet()) {
      if (file.getParentFile().equals(parent)) result.add(myFileToEntryMap.get(file));
    }
    return result;
  }

  @Override
  public boolean isModified(FileObject fileObject, Date entryLastModified, ICvsFileSystem cvsFileSystem) {
    return false;
  }

    @Override
    public boolean isStatic(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
        return false;
    }

    @Override
    public Entry getEntry(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) {
    String path = fileObject.getPath();
    if (StringUtil.startsWithChar(path, '/')) path = path.substring(1);
    return getEntryForPath(new File(path));
  }

  private Entry getEntryForPath(File requested) {
    for (File file : myFileToEntryMap.keySet()) {
      if (file.equals(requested)) return myFileToEntryMap.get(file);
    }
    return null;
  }

  @Override
  public String getRepositoryForDirectory(DirectoryObject directoryObject, String repository, ICvsFileSystem cvsFileSystem) {
    String path = directoryObject.getPath();
    if (StringUtil.startsWithChar(path, '/')) path = path.substring(1);
    return repository + path.replace(File.separatorChar, '/');
  }

  @Override
  public boolean hasCvsDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    return false;
  }

  @Override
  public String getStickyTagForDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    return null;
  }

}
