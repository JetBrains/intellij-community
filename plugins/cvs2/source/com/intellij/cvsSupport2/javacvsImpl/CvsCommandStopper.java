package com.intellij.cvsSupport2.javacvsImpl;

import org.netbeans.lib.cvsclient.ICvsCommandStopper;

/**
 * author: lesya
 */
public class CvsCommandStopper implements ICvsCommandStopper{
  public boolean isAborted() {
    return false;
  }
}
