// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.attach.osHandlers;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.EnvironmentUtil;
import com.intellij.xdebugger.attach.EnvironmentAwareHost;
import com.intellij.xdebugger.attach.LocalAttachHost;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * this class allows to obtain os-specific data from {@link EnvironmentAwareHost}
 */
public abstract class AttachOSHandler {

  private static final Logger LOGGER = Logger.getInstance(AttachOSHandler.class);
  private static final GeneralCommandLine ENV_COMMAND_LINE = new GeneralCommandLine("env");

  private Map<String, String> myEnvironment;
  @NotNull
  private final OSType myOSType;

  @NotNull
  protected final EnvironmentAwareHost myHost;

  public AttachOSHandler(@NotNull EnvironmentAwareHost host, @NotNull final OSType osType) {
    myHost = host;
    myOSType = osType;
  }

  @NotNull
  public OSType getOSType() {
    return myOSType;
  }

  @Nullable
  protected String getenv(String name) throws Exception {
    if(myHost instanceof LocalAttachHost) {
      return EnvironmentUtil.getValue(name);
    }

    if(myEnvironment == null) {
      final String envString = myHost.getProcessOutput(ENV_COMMAND_LINE).getStdout();
      myEnvironment = EnvironmentUtil.parseEnv(envString);
    }

    return myEnvironment.get(name);
  }

  @NotNull
  public static AttachOSHandler getAttachOsHandler(@NotNull EnvironmentAwareHost host) {

    try {
      final OSType osType = computeOsType(host);

      if (osType == OSType.LINUX) {
        return new LinuxAttachOSHandler(host);
      }

      if (osType == OSType.MACOSX) {
        return new MacAttachOSHandler(host);
      }
    }
    catch (ExecutionException e) {
      LOGGER.warn("Error while obtaining host operating system", e);
    }

    return new GenericAttachOSHandler(host);
  }

  @NotNull
  private static OSType localComputeOsType() {
    if(SystemInfo.isLinux) {
      return OSType.LINUX;
    }

    if(SystemInfo.isMac) {
      return OSType.MACOSX;
    }

    return OSType.UNKNOWN;
  }

  @NotNull
  private static OSType computeOsType(@NotNull EnvironmentAwareHost host) throws ExecutionException {
    if(host instanceof LocalAttachHost) {
      return localComputeOsType();
    }

    try {
      GeneralCommandLine getOsCommandLine = new GeneralCommandLine("uname", "-s");
      final String osString = host.getProcessOutput(getOsCommandLine).getStdout().trim();

      OSType osType;

      switch (osString) {
        case "Linux":
          osType = OSType.LINUX;
          break;
        case "Darwin":
          osType = OSType.MACOSX;
          break;
        default:
          osType = OSType.UNKNOWN;
          break;
      }
      return osType;
    }
    catch (ExecutionException ex) {
      throw new ExecutionException("Error while calculating the remote operating system", ex);
    }
  }

  public enum OSType {
    LINUX,
    MACOSX,
    UNKNOWN
  }
}
