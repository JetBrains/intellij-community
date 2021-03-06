// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.attach.osHandlers;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.attach.EnvironmentAwareHost;
import com.intellij.xdebugger.attach.LocalAttachHost;
import org.jetbrains.annotations.NotNull;

/**
 * this class allows to obtain os-specific data from {@link EnvironmentAwareHost}
 */
public abstract class AttachOSHandler {

  private static final Logger LOGGER = Logger.getInstance(AttachOSHandler.class);
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

      if (osType == OSType.WINDOWS) {
        return new GenericAttachOSHandler(host, OSType.WINDOWS);
      }
    }
    catch (ExecutionException e) {
      LOGGER.warn("Error while obtaining host operating system", e);
    }

    return new GenericAttachOSHandler(host, OSType.UNKNOWN);
  }

  @NotNull
  private static OSType localComputeOsType() {
    if(SystemInfo.isLinux) {
      return OSType.LINUX;
    }

    if(SystemInfo.isMac) {
      return OSType.MACOSX;
    }

    if(SystemInfo.isWindows) {
      return OSType.WINDOWS;
    }

    return OSType.UNKNOWN;
  }

  @NotNull
  private static OSType computeOsType(@NotNull EnvironmentAwareHost host) throws ExecutionException {
    if (host instanceof LocalAttachHost) {
      return localComputeOsType();
    }

    try {
      GeneralCommandLine getOsCommandLine = new GeneralCommandLine("uname", "-s");
      final String osString = host.getProcessOutput(getOsCommandLine).getStdout().trim();

      OSType osType;

      //TODO [viuginick] handle remote windows
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
      throw new ExecutionException(XDebuggerBundle.message("dialog.message.error.while.calculating.remote.operating.system"), ex);
    }
  }

  public enum OSType {
    LINUX,
    MACOSX,
    WINDOWS,
    UNKNOWN
  }
}
