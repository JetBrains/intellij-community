/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Alexander Koshevoy
 */
public abstract class DockerSupport {
  @Nullable
  public static DockerSupport getInstance() {
    return ServiceManager.getService(DockerSupport.class);
  }

  public abstract boolean hasDockerMachine();

  /**
   * @deprecated should be moved to application settings
   */
  @Deprecated
  @Nullable
  public abstract String getDockerMachineExecutable();

  /**
   * @deprecated should be moved to application settings
   */
  @Deprecated
  public abstract void setDockerMachineExecutable(String executable);

  @NotNull
  public abstract List<String> getVMs() throws DockerMachineException, DockerMachineCommandException;

  @NotNull
  public abstract String getStatus(@NotNull String machineName);

  public abstract void startMachine(@NotNull String machineName);

  @NotNull
  public abstract ConnectionInfo getConnectionInfo(@NotNull String machineName) throws DockerMachineException, DockerMachineCommandException;

  @NotNull
  public abstract ConnectionInfo getConnectionInfo();

  public static class ConnectionInfo {
    @NotNull private final String myApiUrl;
    @Nullable private final String myCertificatesPath;

    public ConnectionInfo(@NotNull String apiUrl, @Nullable String certificatesPath) {
      myApiUrl = apiUrl;
      myCertificatesPath = certificatesPath;
    }

    @NotNull
    public String getApiUrl() {
      return myApiUrl;
    }

    @Nullable
    public String getCertificatesPath() {
      return myCertificatesPath;
    }
  }
}
