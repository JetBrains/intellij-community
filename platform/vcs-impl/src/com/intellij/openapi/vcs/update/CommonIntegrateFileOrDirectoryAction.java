// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class CommonIntegrateFileOrDirectoryAction extends AbstractCommonUpdateAction{
  public CommonIntegrateFileOrDirectoryAction() {
    super(ActionInfo.INTEGRATE, ScopeInfo.FILES, true);
  }

  @Override
  protected boolean filterRootsBeforeAction() {
    return true;
  }
}
