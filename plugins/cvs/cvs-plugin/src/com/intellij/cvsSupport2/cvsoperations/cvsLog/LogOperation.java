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
package com.intellij.cvsSupport2.cvsoperations.cvsLog;

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.BranchesProvider;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsHelper;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.openapi.vcs.FilePath;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.log.LogCommand;
import org.netbeans.lib.cvsclient.command.log.LogInformation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * author: lesya
 */
public class LogOperation extends CvsOperationOnFiles implements BranchesProvider{
  private final List<LogInformation> myLogInformationList = new ArrayList<>();

  public LogOperation(Collection<FilePath> files){
    for (final FilePath file : files) {
      addFile(file.getIOFile());
    }
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    LogCommand command = new LogCommand();
    addFilesToCommand(root, command);
    command.setHeaderOnly(true);
    return command;
  }

  public void fileInfoGenerated(Object info) {
    super.fileInfoGenerated(info);
    if (info instanceof LogInformation) {
      myLogInformationList.add((LogInformation)info);
    }
  }

  public Collection<String> getAllBranches() {
    return TagsHelper.getAllBranches(myLogInformationList);
  }

  public Collection<CvsRevisionNumber> getAllRevisions() {
    return null;
  }

  public List<LogInformation> getLogInformationList() {
    return myLogInformationList;
  }

  protected String getOperationName() {
    return "log";
  }

  public int getFilesToProcessCount() {
    return -1;
  }
}
