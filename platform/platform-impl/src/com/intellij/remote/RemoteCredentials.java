/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.remote;

import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public interface RemoteCredentials {
  String getHost();

  int getPort();

  String getLiteralPort();

  @Transient
  String getUserName();

  String getPassword();

  @Transient
  String getPassphrase();

  @NotNull
  AuthType getAuthType();

  String getPrivateKeyFile();

  boolean isStorePassword();

  boolean isStorePassphrase();

  /**
   * By default when user connects to a remote server, host fingerprint should be verified via
   * <pre>~/.ssh/known_hosts</pre> file and user should explicitly confirm connection if he never
   * connected to the remote host before. When remote host is trusted regardless of known hosts file
   * (for example, when connecting to Vagrant VM), confirmation should be skipped.
   *
   * @return true if host key verification should be skipped.
   */
  default boolean isSkippingHostKeyVerification() {
    return false;
  }
}
