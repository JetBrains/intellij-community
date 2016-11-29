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
import com.intellij.cvsSupport2.connections.login.CvsLoginWorker;
import com.intellij.cvsSupport2.errorHandling.CvsException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Consumer;
import com.intellij.util.ThreeState;
import com.intellij.util.ui.UIUtil;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;

import java.util.Collection;

public class LoginPerformer {
  private final Project myProject;
  private final Collection<CvsEnvironment> myRoots;
  private final Consumer<VcsException> myExceptionConsumer;
  private boolean myForceCheck;

  public LoginPerformer(final Project project, Collection<CvsEnvironment> roots, Consumer<VcsException> exceptionConsumer) {
    myProject = project;
    myRoots = roots;
    myExceptionConsumer = exceptionConsumer;
    myForceCheck = false;
  }

  public void setForceCheck(boolean forceCheck) {
    myForceCheck = forceCheck;
  }

  public boolean loginAll() {
    return loginAll(true);
  }

  public boolean loginAll(final boolean goOffline) {
    for (CvsEnvironment root : myRoots) {
      final CvsLoginWorker worker = root.getLoginWorker(myProject);

      try {
        final ThreeState checkResult = checkLoginWorker(worker, myForceCheck);
        if (! ThreeState.YES.equals(checkResult)) {
          if (ThreeState.UNSURE.equals(checkResult)) {
            if (goOffline) {
              worker.goOffline();
            }
            myExceptionConsumer.consume(new CvsException("Authentication canceled", root.getCvsRootAsString()));
          }
          return false;
        }
      } catch (AuthenticationException e) {
        myExceptionConsumer.consume(new CvsException(e, root.getCvsRootAsString()));
        return false;
      }
    }
    return true;
  }

  public static ThreeState checkLoginWorker(final CvsLoginWorker worker, final boolean forceCheckParam)
    throws AuthenticationException {
    boolean forceCheck = forceCheckParam;
    final Ref<Boolean> promptResult = new Ref<>();
    final Runnable prompt = () -> promptResult.set(worker.promptForPassword());
    while (true) {
      final ThreeState state = worker.silentLogin(forceCheck);
      if (ThreeState.YES.equals(state)) return ThreeState.YES;
      if (ThreeState.NO.equals(state)) return state;
      try {
        // hack: allow indeterminate progress bar time to appear before displaying login dialog.
        // otherwise progress bar without cancel button appears on top of login dialog, blocking input and hanging IDEA.
        Thread.sleep(1000L);
      }
      catch (InterruptedException ignore) {
        return ThreeState.NO;
      }
      UIUtil.invokeAndWaitIfNeeded(prompt);
      if (! Boolean.TRUE.equals(promptResult.get())) {
        return ThreeState.UNSURE; // canceled
      }
      forceCheck = true;
    }
  }
}
