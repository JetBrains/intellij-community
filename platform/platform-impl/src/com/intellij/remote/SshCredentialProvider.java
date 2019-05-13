// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * This provider is needed to obtain SSH credentials(e.g. implementation based on Remote SDK's, Deployment configurations)
 */
public interface SshCredentialProvider {
  ExtensionPointName<SshCredentialProvider> EP_NAME = ExtensionPointName.create("com.intellij.sshCredentialProvider");

  /**
   * provides a list of {@link RemoteCredentials} provided by the concrete provider
   * @throws ExecutionException if obtaining {@link RemoteCredentials} collections failed
   */
  @NotNull
  Collection<RemoteCredentials> getCredentialsList(@Nullable Project project) throws ExecutionException;
}
