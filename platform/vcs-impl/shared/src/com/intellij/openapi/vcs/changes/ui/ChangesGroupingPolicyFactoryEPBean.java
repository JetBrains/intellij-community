// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public final class ChangesGroupingPolicyFactoryEPBean extends BaseKeyedLazyInstance<ChangesGroupingPolicyFactory> {
  @ApiStatus.Internal
  public static final int DEFAULT_WEIGHT = 100;

  @RequiredElement
  @Attribute("key")
  public String key;

  /**
   * Higher weights are processed earlier, and their nodes are located closer to the root.
   */
  @Attribute("weight")
  public int weight = DEFAULT_WEIGHT;

  @RequiredElement
  @Attribute("implementationClass")
  public String implementationClass;

  @ApiStatus.Internal
  public ChangesGroupingPolicyFactoryEPBean() {
  }

  @ApiStatus.Internal
  @Override
  protected @Nullable String getImplementationClassName() {
    return implementationClass;
  }
}
