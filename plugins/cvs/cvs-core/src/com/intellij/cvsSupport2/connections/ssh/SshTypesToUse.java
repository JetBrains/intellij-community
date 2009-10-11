/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
