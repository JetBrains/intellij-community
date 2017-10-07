// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.junit.Assert;

import java.util.Arrays;
import java.util.Set;

@TestOnly
public class SdkLeakTracker {
  @NotNull
  private final Sdk[] oldSdks;
  public SdkLeakTracker() {
    ProjectJdkTable table = ProjectJdkTable.getInstance();
    oldSdks = table == null ? new Sdk[0] : table.getAllJdks();
  }

  public void checkForJdkTableLeaks() {
    ProjectJdkTable table = ProjectJdkTable.getInstance();
    if (table != null) {
      Sdk[] jdks = table.getAllJdks();
      if (jdks.length != 0) {
        Set<Sdk> leaked = new THashSet<>(Arrays.asList(jdks));
        Set<Sdk> old = new THashSet<>(Arrays.asList(oldSdks));
        leaked.removeAll(old);

        try {
          if (!leaked.isEmpty()) {
            Assert.fail("Leaked SDKs: " + leaked);
          }
        }
        finally {
          for (Sdk jdk : leaked) {
            WriteAction.run(()-> table.removeJdk(jdk));
          }
        }
      }
    }
  }

}
