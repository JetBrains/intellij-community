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
package com.intellij.cvsSupport2.cvsoperations.cvsImport;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsCommandOperation;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.importcmd.ImportCommand;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.util.IIgnoreFileFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class ImportOperation extends CvsCommandOperation {
  private final ImportDetails myDetails;

  public ImportOperation(ImportDetails details) {
    myDetails = details;
  }

  @Override
  protected Collection<CvsRootProvider> getAllCvsRoots() {
    return Collections.singleton(myDetails.getCvsRoot());
  }

  @Override
  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    final ImportCommand result = new ImportCommand();
    myDetails.prepareCommand(result);
    return result;
  }

  public static ImportOperation createTestInstance(File sourceLocation, CvsEnvironment env) {
    final ImportDetails details = new ImportDetails(sourceLocation, CvsBundle.message("import.defaults.vendor"),
                                                    CvsBundle.message("import.defaults.release_tag"),
                                                    CvsBundle.message("import.defaults.log.message"),
                                                    sourceLocation.getName(), env, new ArrayList(),
                                                    new IIgnoreFileFilter(){
                                                      @Override
                                                      public boolean shouldBeIgnored(AbstractFileObject abstractFileObject, ICvsFileSystem cvsFileSystem) {
                                                        return false;
                                                      }
                                                    });
    return new ImportOperation(details);
  }

  @Override
  public int getFilesToProcessCount() {
    return 2 * myDetails.getTotalFilesInSourceDirectory();
  }

  @Override
  protected String getOperationName() {
    return "import";
  }

  @Override
  protected IIgnoreFileFilter getIgnoreFileFilter() {
    return myDetails.getIgnoreFileFilter();
  }
}
