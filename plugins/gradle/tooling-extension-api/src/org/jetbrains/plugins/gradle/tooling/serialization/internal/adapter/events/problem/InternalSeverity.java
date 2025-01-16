// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem;

import org.gradle.tooling.events.problems.Severity;

public class InternalSeverity implements Severity {

  private final int severity;
  private final boolean isKnown;

  public InternalSeverity(int severity, boolean isKnown) {
    this.severity = severity;
    this.isKnown = isKnown;
  }

  @Override
  public int getSeverity() {
    return severity;
  }

  @Override
  public boolean isKnown() {
    return isKnown;
  }
}
