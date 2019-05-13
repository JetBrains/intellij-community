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
package com.intellij.cvsSupport2.connections.login;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import org.jetbrains.annotations.CalledInBackground;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.ThreeState;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;

public abstract class CvsLoginWorkerImpl<T extends CvsConnectionSettings> implements CvsLoginWorker {
  protected final Project myProject;
  protected final T mySettings;

  protected CvsLoginWorkerImpl(final Project project, final T settings) {
    myProject = project;
    mySettings = settings;
  }

  @CalledInBackground
  protected abstract void silentLoginImpl(boolean forceCheck) throws AuthenticationException;

  protected abstract void clearOldCredentials();

  @Override
  public ThreeState silentLogin(boolean forceCheck) throws AuthenticationException {
    if (mySettings.isOffline())  return ThreeState.NO;

    try {
      silentLoginImpl(forceCheck);
    }
    catch (AuthenticationException e) {
      if (e.isSolveable()) {
        clearOldCredentials();
        return ThreeState.UNSURE;
      }
      throw e;
    }
    return ThreeState.YES;
  }

  @Override
  public void goOffline() {
    mySettings.setOffline(true);
    VcsBalloonProblemNotifier.showOverChangesView(myProject, CvsBundle.message("set.offline.notification.text"), MessageType.WARNING);
  }

  public static void showConnectionErrorMessage(final Project project, final String message) {
    VcsBalloonProblemNotifier.showOverChangesView(project, message, MessageType.ERROR);
  }
}
