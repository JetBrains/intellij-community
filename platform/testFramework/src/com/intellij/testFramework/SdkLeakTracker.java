// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.junit.Assert;

import java.util.Set;

@TestOnly
public final class SdkLeakTracker {
  private final Sdk @NotNull [] oldSdks;

  public SdkLeakTracker() {
    ProjectJdkTable table = ProjectJdkTable.getInstance();
    oldSdks = table == null ? new Sdk[0] : table.getAllJdks();
  }

  public void checkForJdkTableLeaks() {
    ProjectJdkTable table = ProjectJdkTable.getInstance();
    if (table == null) {
      return;
    }

    Sdk[] jdks = table.getAllJdks();
    if (jdks.length == 0) {
      return;
    }

    Set<Sdk> leaked = ContainerUtil.set(jdks);
    Set<Sdk> old = ContainerUtil.set(oldSdks);
    leaked.removeAll(old);

    try {
      if (!leaked.isEmpty()) {
        Assert.fail("Leaked SDKs: " + leaked+". Please remove leaking SDKs by e.g. ProjectJdkTable.getInstance().removeJdk() or by disposing the ProjectJdkImpl");
      }
    }
    finally {
      for (Sdk jdk : leaked) {
        WriteAction.run(() -> table.removeJdk(jdk));
      }
    }
  }
}
