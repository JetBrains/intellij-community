package com.intellij.cvsSupport2.connections;

import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.ICvsCommandStopper;

/**
 * author: lesya
 */
public interface SelfTestingConnection {
  void test(ICvsCommandStopper stopper) throws AuthenticationException;
}
