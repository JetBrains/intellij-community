// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

public class VagrantNotStartedException extends RuntimeException {
  private final String myVagrantFolder;
  private final String myMachineName;

  public VagrantNotStartedException(String message, String vagrantFolder, String machineName) {
    super(message);
    myVagrantFolder = vagrantFolder;

    myMachineName = machineName;
  }

  public String getVagrantFolder() {
    return myVagrantFolder;
  }

  public String getMachineName() {
    return myMachineName;
  }
}
