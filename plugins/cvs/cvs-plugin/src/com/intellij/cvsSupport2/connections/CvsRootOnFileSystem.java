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
package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.application.CvsInfo;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * author: lesya
 */
public class CvsRootOnFileSystem extends CvsRootProvider {
  private final CvsConnectionSettings mySettings;
  private CvsRootOnFileSystem(CvsConnectionSettings cvsRoot, File fileRoot) {
    super(fileRoot, cvsRoot);
    mySettings = cvsRoot;
  }

  public static CvsRootOnFileSystem createMeOn(File file) throws CannotFindCvsRootException {
    File nearestRoot = getRootFor(file);
    if (nearestRoot == null) throw new CannotFindCvsRootException(file);
    CvsConnectionSettings cvsRoot = getCvsRootFor(nearestRoot);
    if (cvsRoot == CvsInfo.getAbsentSettings()) throw new CannotFindCvsRootException(file);
    File commonRoot = getCommonRoot(nearestRoot, cvsRoot);
    return new CvsRootOnFileSystem(getCvsEntriesManager().getCvsConnectionSettingsFor(commonRoot), commonRoot);
  }

  private static CvsEntriesManager getCvsEntriesManager() {
    return CvsEntriesManager.getInstance();
  }


  public int hashCode() {
    return mySettings.hashCode() ^ getLocalRoot().hashCode();
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof CvsRootOnFileSystem)) return false;
    CvsRootOnFileSystem other = (CvsRootOnFileSystem) obj;
    return mySettings.equals(other.mySettings) &&
        getLocalRoot().equals(other.getLocalRoot());
  }

  private static File getCommonRoot(File nearestRoot, CvsConnectionSettings cvsRoot) {
    File result = nearestRoot;
    if (result.getParentFile() == null) return result;
    while (cvsRoot.equals(getCvsRootFor(result.getParentFile()))) {
      result = result.getParentFile();
      if (result.getParentFile() == null) return result;
    }
    return result;
  }

  private static CvsConnectionSettings getCvsRootFor(@NotNull File file) {
    return getCvsEntriesManager().getCvsConnectionSettingsFor(file);
  }

  @Nullable
  private static File getRootFor(File file) {
    if (file == null) return null;
    if (!file.isDirectory()) return getRootFor(file.getParentFile());
    if (CvsUtil.fileIsUnderCvs(file)) return file;
    return getRootFor(file.getParentFile());
  }

  @Override
  public String getRepository() {
    return myCvsEnvironment.getRepository();
  }

  @Override
  public RevisionOrDate getRevisionOrDate() {
    return RevisionOrDate.EMPTY;
  }
}


