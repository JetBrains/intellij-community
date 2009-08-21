package com.intellij.cvsSupport2.connections.ssh;

import org.jetbrains.annotations.NonNls;

/**
 * @author Thomas Singer
 */
public final class SshTypesToUse {

  // Constants ==============================================================

  public static final SshTypesToUse ALLOW_BOTH = new SshTypesToUse("SSH1, SSH2");
  public static final SshTypesToUse FORCE_SSH1 = new SshTypesToUse("SSH1");
  public static final SshTypesToUse FORCE_SSH2 = new SshTypesToUse("SSH2");

  // Fields =================================================================

  private final String name;

  // Setup ==================================================================

  private SshTypesToUse(@NonNls String name) {
    this.name = name;
  }

  // Implemented ============================================================

  public String toString() {
    return name;
  }

  public static SshTypesToUse fromName(final String sshType) {
    if (FORCE_SSH1.name.equals(sshType)) {
      return FORCE_SSH1;
    }
    if (FORCE_SSH2.name.equals(sshType)) {
      return FORCE_SSH2;
    }
    return ALLOW_BOTH;
  }
}
