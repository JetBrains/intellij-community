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

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls.ConstantLocalFileReader;
import com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls.DeafAdminReader;
import com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls.DeafAdminWriter;
import org.netbeans.lib.cvsclient.admin.IAdminReader;
import org.netbeans.lib.cvsclient.admin.IAdminWriter;
import org.netbeans.lib.cvsclient.file.ILocalFileReader;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public abstract class LocalPathIndifferentOperation extends CvsCommandOperation {
  protected final CvsEnvironment myEnvironment;

  public LocalPathIndifferentOperation(CvsEnvironment environment) {
    super(new DeafAdminReader(), new DeafAdminWriter());
    myEnvironment = environment;
  }

  public LocalPathIndifferentOperation(IAdminReader adminReader, IAdminWriter writer, CvsEnvironment environment) {
    super(adminReader, writer);
    myEnvironment = environment;
  }

  public LocalPathIndifferentOperation(IAdminReader adminReader, CvsEnvironment environment) {
    super(adminReader, new DeafAdminWriter());
    myEnvironment = environment;
  }

  public LocalPathIndifferentOperation(IAdminWriter adminWriter, CvsEnvironment environment) {
    super(new DeafAdminReader(), adminWriter);
    myEnvironment = environment;
  }

  @Override
  protected Collection<CvsRootProvider> getAllCvsRoots() {
    return Collections.singleton(getCvsRootProvider());
  }

  public CvsRootProvider getCvsRootProvider() {
    return CvsRootProvider.createOn(getPathToCommonRoot(), myEnvironment);
  }

  protected File getPathToCommonRoot() {
    File someFile = new File("").getAbsoluteFile();
    if (someFile.isDirectory()) return someFile;
    return someFile.getAbsoluteFile().getParentFile();
  }

  @Override
  protected ILocalFileReader createLocalFileReader() {
    return ConstantLocalFileReader.FOR_EXISTING_FILE;
  }

  @Override
  protected boolean shouldMakeChangesOnTheLocalFileSystem() {
    return false;
  }

  @Override
  public String getLastProcessedCvsRoot() {
    return myEnvironment.getCvsRootAsString();
  }
}
