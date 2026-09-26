// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class CommonIntegrateProjectAction extends AbstractCommonUpdateAction{

  public CommonIntegrateProjectAction() {
    super(ActionInfo.INTEGRATE, ScopeInfo.PROJECT, true);
  }

  @Override
  protected boolean filterRootsBeforeAction() {
    return false;
  }
}
