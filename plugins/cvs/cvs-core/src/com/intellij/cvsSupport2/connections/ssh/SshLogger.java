package com.intellij.cvsSupport2.connections.ssh;

import com.intellij.openapi.diagnostic.Logger;

public class SshLogger {
  private final static Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.connections.ssh.SshLogger");

  public static void debug(final String s) {
    LOG.debug(s);
  }

  public static void debug(final String s, final Throwable t) {
    LOG.debug(s, t);
  }
}
