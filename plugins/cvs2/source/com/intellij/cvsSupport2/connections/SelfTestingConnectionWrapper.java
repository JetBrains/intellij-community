package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.connections.SelfTestingConnection;
import com.intellij.cvsSupport2.connections.ConnectionWrapper;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import org.netbeans.lib.cvsclient.ICvsCommandStopper;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.IConnection;

/**
 * author: lesya
 */
public class SelfTestingConnectionWrapper extends ConnectionWrapper implements SelfTestingConnection{
  public SelfTestingConnectionWrapper(IConnection sourceConnection, ReadWriteStatistics statistics, ICvsCommandStopper cvsCommandStopper) {
    super(sourceConnection, statistics, cvsCommandStopper);
  }

  public void test(ICvsCommandStopper stopper) throws AuthenticationException {
    ((SelfTestingConnection)mySourceConnection).test(stopper);
  }
}
