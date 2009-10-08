package com.intellij.cvsSupport2.javacvsImpl;

import org.netbeans.lib.cvsclient.ICvsCommandStopper;

/**
 * author: lesya
 */
public class CvsCommandStopper implements ICvsCommandStopper{
  private volatile boolean myPing;

  public boolean isAborted() {
    myPing = true;
    return false;
  }

  public boolean isAlive() {
    return myPing;
  }

  public void resetAlive() {
    myPing = false;
  }
}
