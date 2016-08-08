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
package com.intellij.cvsSupport2.cvsoperations.cvsErrors;

import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesAdapter;
import com.intellij.cvsSupport2.errorHandling.CvsException;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

import java.util.ArrayList;
import java.util.List;

public class ErrorMessagesProcessor extends CvsMessagesAdapter implements ErrorProcessor {
  private final List<VcsException> myErrors;

  public ErrorMessagesProcessor(List<VcsException> errors) {
    myErrors = errors;
  }

  public ErrorMessagesProcessor() {
    this(new ArrayList<>());
  }

  public void addError(String message, String relativeFilePath, ICvsFileSystem cvsFileSystem, String cvsRoot, boolean warning) {
    VirtualFile vFile = getVirtualFile(cvsFileSystem, relativeFilePath);
    VcsException vcsException = new CvsException(message, cvsRoot);
    if (vFile != null) vcsException.setVirtualFile(vFile);
    vcsException.setIsWarning(warning);
    myErrors.add(vcsException);
  }

  private static VirtualFile getVirtualFile(ICvsFileSystem cvsFileSystem, String relativeFileName) {
    if (cvsFileSystem == null) return null;
    if (relativeFileName == null) return null;
    return CvsVfsUtil.findFileByIoFile(cvsFileSystem.getLocalFileSystem().getFile(relativeFileName));
  }

  public List<VcsException> getErrors() {
    return myErrors;
  }

  public void addError(VcsException ex) {
    myErrors.add(ex);
  }
}
