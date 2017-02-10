/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.LocalPathIndifferentOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsLog.RlogCommand;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.command.Command;

import java.util.Collection;

public class GetAllBranchesOperation extends LocalPathIndifferentOperation implements BranchesProvider {
  private final Collection<String> myTags = new HashSet<>();
  @NonNls private final static String START = "symbolic names:";
  @NonNls private final static String END = "keyword substitution:";
  private boolean myIsInBranchesMode = false;
  private final String myModuleName;

  public GetAllBranchesOperation(CvsEnvironment environment) {
    this(environment, ".");
  }

  public GetAllBranchesOperation(CvsEnvironment environment, String moduleName) {
    super(environment);
    myModuleName = moduleName;
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    final RlogCommand command = new RlogCommand();
    command.setModuleName(myModuleName);
    // TODO[yole]: it would be best to implement smarter handling similar to LoadHistoryOperation, but it's too cumbersome without a major refactoring
    command.setSuppressEmptyHeaders(false);  // see IDEADEV-14276
    return command;
  }

  public void messageSent(String message, final byte[] byteMessage, boolean error, boolean tagged) {
    if (error) return;
    if (tagged) return;
    if (message.startsWith(START)) {
      myIsInBranchesMode = true;
      return;
    }

    if (message.startsWith(END)) {
      myIsInBranchesMode = false;
      return;
    }

    if (myIsInBranchesMode) {
      String trimmedMessage = message.trim();
      int lastIndex = trimmedMessage.indexOf(":");
      if (lastIndex >= 0) {
        myTags.add(trimmedMessage.substring(0, lastIndex));
      }
    }
  }

  public Collection<String> getAllBranches(){
    return myTags;
  }

  public Collection<CvsRevisionNumber> getAllRevisions() {
    return null;
  }

  protected String getOperationName() {
    return "rlog";
  }
}
