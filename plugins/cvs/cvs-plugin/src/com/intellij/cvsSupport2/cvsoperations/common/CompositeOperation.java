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
package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddFileOperation;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CompositeOperation extends CvsOperation {
  private final List<CvsOperation> mySubOperations = new ArrayList<>();
  private CvsOperation myCurrentOperation;

  public void addOperation(CvsOperation operation) {
    mySubOperations.add(operation);
  }

  protected void addOperation(final int i, final CvsOperation operation) {
    mySubOperations.add(i, operation);
  }

  @Override
  public void appendSelfCvsRootProvider(@NotNull final Collection<CvsEnvironment> roots) throws CannotFindCvsRootException {
    for (CvsOperation operation : mySubOperations) {
      operation.appendSelfCvsRootProvider(roots);
    }
  }

  public void execute(CvsExecutionEnvironment executionEnvironment, boolean underReadAction) throws VcsException, CommandAbortedException {
    for (final CvsOperation cvsOperation : getSubOperations()) {
      myCurrentOperation = cvsOperation;
      myCurrentOperation.execute(executionEnvironment, underReadAction);
    }
  }

  public void executeFinishActions() {
    super.executeFinishActions();
    for (final CvsOperation cvsOperation : getSubOperations()) {
      cvsOperation.executeFinishActions();
    }
  }

  public String getLastProcessedCvsRoot() {
    if (myCurrentOperation == null) return null;
    return myCurrentOperation.getLastProcessedCvsRoot();
  }

  protected boolean containsSubOperation(AddFileOperation op) {
    return mySubOperations.contains(op);
  }

  protected List<CvsOperation> getSubOperations() { return mySubOperations; }

  public boolean runInReadThread() {
    for(CvsOperation op: mySubOperations) {
      if (op.runInReadThread()) return true;
    }
    return false;
  }

  protected int getSubOperationsCount() {
    return mySubOperations.size();
  }
}
