// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

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
        String message = "Leaked SDKs: " + leaked + ". " +
                         "Please remove leaking SDKs by e.g. ProjectJdkTable.getInstance().removeJdk() or by disposing the ProjectJdkImpl";
        Pair<Sdk, Throwable> withTrace = findSdkWithRegistrationTrace(leaked);
        if (withTrace != null) {
          throw new AssertionError(message + ". Registration trace for '" + withTrace.first.getName() + "' is shown as the cause",
                                   withTrace.second);
        }
        else {
          throw new AssertionError(message);
        }
      }
    }
    finally {
      for (Sdk jdk : leaked) {
        WriteAction.run(() -> table.removeJdk(jdk));
      }
    }
  }

  private static Pair<Sdk, Throwable> findSdkWithRegistrationTrace(Set<? extends Sdk> sdks) {
    for (Sdk sdk : sdks) {
      if (sdk instanceof Disposable) {
        Throwable trace = Disposer.getRegistrationTrace((Disposable)sdk);
        if (trace != null) {
          return new Pair<>(sdk, trace);
        }
      }
    }
    return null;
  }
}
