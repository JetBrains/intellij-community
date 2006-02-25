package com.intellij.cvsSupport2.connections.sshViaMaverick;

/**
 * author: lesya
 */
public interface SmPublicKeyAuthentification {
  public Throwable PRIVATE_KEY_FILE = new Throwable();
  public Throwable PASSPHRASE = new Throwable();
}
