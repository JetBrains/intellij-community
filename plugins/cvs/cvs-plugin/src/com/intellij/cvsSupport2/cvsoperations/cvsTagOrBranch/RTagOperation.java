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
package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.LocalPathIndifferentOperation;
import com.intellij.cvsSupport2.cvsoperations.common.LocalPathIndifferentOperationHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.containers.HashMap;
import org.netbeans.lib.cvsclient.command.Command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RTagOperation extends LocalPathIndifferentOperation {
  private final String myTagName;
  private final LocalPathIndifferentOperationHelper myHelper = new LocalPathIndifferentOperationHelper();
  private final boolean myOverrideExistings;

  public static RTagOperation[] createOn(FilePath[] files, String tagName, boolean overrideExisting) {
    Map<CvsEnvironment, List<File>> envToFiles = new HashMap<>();
    for (FilePath file : files) {
      CvsConnectionSettings cvsConnectionSettings = CvsUtil.getCvsConnectionSettings(file);
      if (!envToFiles.containsKey(cvsConnectionSettings)) envToFiles.put(cvsConnectionSettings, new ArrayList<>());
      envToFiles.get(cvsConnectionSettings).add(file.getIOFile());
    }

    ArrayList<RTagOperation> result = new ArrayList<>();
    for (CvsEnvironment cvsEnvironment : envToFiles.keySet()) {
      RTagOperation rTagOperation = new RTagOperation(cvsEnvironment, tagName, overrideExisting);
      result.add(rTagOperation);
      List<File> iofiles = envToFiles.get(cvsEnvironment);
      for (File file : iofiles) {
        rTagOperation.addFile(file);
      }
    }

    return result.toArray(new RTagOperation[result.size()]);
  }

  public RTagOperation(CvsEnvironment environment, String tagName, boolean overrideExisting) {
    super(environment);
    myTagName = tagName;
    myOverrideExistings = overrideExisting;
  }

  public void addFile(File file) {
    myHelper.addFile(CvsUtil.getCvsLightweightFileForFile(file));
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    RtagCommand result = new RtagCommand(myTagName);
    myHelper.addFilesTo(result);
    result.setOverrideExistings(myOverrideExistings);
    return result;
  }

  protected String getOperationName() {
    return "Tag";
  }
}