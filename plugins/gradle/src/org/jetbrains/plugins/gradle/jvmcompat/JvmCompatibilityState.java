// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat;

public class JvmCompatibilityState {
  public CompatibilityData data;
  public String ideVersion;
  public boolean isDefault;
  public long lastUpdateTime;
}
