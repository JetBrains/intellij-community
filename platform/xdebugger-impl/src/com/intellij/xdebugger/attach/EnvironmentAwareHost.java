// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.attach;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.project.Project;
import com.intellij.remote.RemoteSdkException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.Arrays;

@ApiStatus.Experimental
public abstract class EnvironmentAwareHost implements XAttachHost {

  private OSType myOSType;

  public abstract boolean isUnix() throws ExecutionException;

  /**
   * @param command commandLine to execute on this host
   * @return output of the corresponding process
   */
  @NotNull
  public abstract ProcessOutput execAndGetOutput(@Nullable Project project,
                                 @NotNull GeneralCommandLine command) throws ExecutionException;


  /**
   * Retrieves remote file contents stream. May be used to sync parts of the debugged project.
   * @param remotePath host-dependent path
   * @return stream with file contents or <code>null</code> if the specified file does not exist
   * @throws ExecutionException on stream retrieval error
   */
  @Nullable
  public abstract InputStream getRemoteFile(@NotNull String remotePath) throws ExecutionException;

  /**
   * @return uid of user or -1 if error occurred
   */
  public int getUid(@NotNull Project project) throws ExecutionException {
    OSType osType = getOsType(project);

    if (osType == OSType.LINUX || osType == OSType.MACOSX) {
      try {
        return getUidUnix(project);
      }
      catch (RemoteSdkException ex) {
        return -1;
      }
    }
    else {
      return -1;
    }
  }

  private int getUidUnix(Project project) throws ExecutionException {

    GeneralCommandLine commandLine = new GeneralCommandLine(Arrays.asList("id", "-u"));
    ProcessOutput uidOutput = execAndGetOutput(project, commandLine);

    String uid = uidOutput.getStdout().trim();

    try {
      return Integer.valueOf(uid);
    }
    catch (NumberFormatException e) {
      throw new ExecutionException("Error while parsing uid");
    }
  }

  public OSType getOsType(Project project) throws ExecutionException {
    if (myOSType != null) {
      return myOSType;
    }

    try {
      GeneralCommandLine getOsCommandLine = new GeneralCommandLine("uname", "-s");
      ProcessOutput getOsOutput = execAndGetOutput(project, getOsCommandLine);

      String osString = getOsOutput.getStdout().trim();

      OSType osType;

      switch (osString) {
        case "Linux":
          osType = OSType.LINUX;
          break;
        case "Darwin":
          osType = OSType.MACOSX;
          break;
        case "WindowsNT":
          osType = OSType.WINDOWS;
          break;
        default:
          osType = OSType.UNKNOWN;
          break;
      }

      return myOSType = osType;
    }
    catch (ExecutionException ex) {
      throw new ExecutionException("Error while calculating the remote operating system");
    }
  }

  abstract public boolean isSudoNeeded(@NotNull ProcessInfo info);

  public enum OSType {
    LINUX,
    MACOSX,
    WINDOWS,
    UNKNOWN
  }
}
