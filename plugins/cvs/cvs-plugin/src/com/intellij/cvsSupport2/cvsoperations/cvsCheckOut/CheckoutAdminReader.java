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
package com.intellij.cvsSupport2.cvsoperations.cvsCheckOut;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import org.netbeans.lib.cvsclient.admin.AdminReader;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.admin.IAdminReader;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

/**
 * author: lesya
 */
public class CheckoutAdminReader implements IAdminReader{
  private final IAdminReader myStandardAdminReader = new AdminReader(CvsApplicationLevelConfiguration.getCharset());
  @Override
  public Entry getEntry(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) {
    return null;
  }

  @Override
  public Collection getEntries(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    return new ArrayList();
  }

  @Override
  public String getRepositoryForDirectory(DirectoryObject directoryObject, String repository, ICvsFileSystem cvsFileSystem) throws IOException {
    return myStandardAdminReader.getRepositoryForDirectory(directoryObject, repository, cvsFileSystem);
  }

  @Override
  public String getStickyTagForDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    return null;
  }

  @Override
  public boolean hasCvsDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    return myStandardAdminReader.hasCvsDirectory(directoryObject, cvsFileSystem);
  }

  @Override
  public boolean isModified(FileObject fileObject, Date entryLastModified, ICvsFileSystem cvsFileSystem) {
    return false;
  }

  @Override
  public boolean isStatic(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    return false;
  }
}
