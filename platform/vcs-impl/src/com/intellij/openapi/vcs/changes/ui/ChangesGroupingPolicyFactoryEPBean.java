// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ChangesGroupingPolicyFactoryEPBean extends BaseKeyedLazyInstance<ChangesGroupingPolicyFactory> {
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

  @Override
  protected @Nullable String getImplementationClassName() {
    return implementationClass;
  }
}
