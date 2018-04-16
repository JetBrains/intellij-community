// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface SshCredentialProducer {
  ExtensionPointName<SshCredentialProducer> EP_NAME = ExtensionPointName.create("com.intellij.sshCredentialProducer");

  Collection<RemoteCredentials> getCredentialsList(@NotNull Project project);
}
