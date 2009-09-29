package com.intellij.cvsSupport2.connections.login;

import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.openapi.vcs.CalledInBackground;
import com.intellij.util.ThreeState;

// todo rename?
public interface CvsLoginWorker {
  /**
   * @return <code>true</code> if login attempt should be repeated after prompting user
   */
  @CalledInAwt
  boolean promptForPassword();

  @CalledInBackground
  ThreeState silentLogin(boolean forceCheck);

  void goOffline();
}
