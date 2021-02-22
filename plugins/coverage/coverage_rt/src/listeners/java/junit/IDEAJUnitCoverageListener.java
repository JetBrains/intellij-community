// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage.listeners.java.junit;

import com.intellij.coverage.listeners.java.CoverageListener;
import com.intellij.rt.execution.junit.IDEAJUnitListener;

@SuppressWarnings({"UnnecessaryFullyQualifiedName"})
public class IDEAJUnitCoverageListener extends CoverageListener implements IDEAJUnitListener {

  public void testStarted(String className, String methodName) {
    final Object data = getData();
    ((com.intellij.rt.coverage.data.ProjectData)data).testStarted(sanitize(className, methodName));
  }

  public void testFinished(String className, String methodName) {
    final Object data = getData();
    ((com.intellij.rt.coverage.data.ProjectData)data).testEnded(sanitize(className, methodName));
  }
}