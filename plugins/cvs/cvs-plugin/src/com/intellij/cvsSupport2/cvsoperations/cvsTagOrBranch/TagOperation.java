/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import com.intellij.openapi.vcs.FilePath;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.tag.TagCommand;

/**
 * author: lesya
 */
public class TagOperation extends CvsOperationOnFiles{

  private final String myTag;
  private final boolean myRemoveTag;
  private final boolean myOverrideExisting;

  public TagOperation(FilePath[] files, String tag, boolean removeTag, boolean overrideExisting) {
    for (FilePath file : files) {
      addFile(file.getIOFile());
    }
    myTag = tag;
    myRemoveTag = removeTag;
    myOverrideExisting = overrideExisting;
  }

  @Override
  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    final TagCommand tagCommand = new TagCommand();
    addFilesToCommand(root, tagCommand);
    tagCommand.setTag(myTag);
    tagCommand.setDeleteTag(myRemoveTag);
    tagCommand.setOverrideExistingTag(myOverrideExisting);
    return tagCommand;
  }

  @Override
  protected String getOperationName() {
    return "tag";
  }
}
