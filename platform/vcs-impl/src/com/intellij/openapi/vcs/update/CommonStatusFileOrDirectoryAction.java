// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class CommonStatusFileOrDirectoryAction extends AbstractCommonUpdateAction{
  public CommonStatusFileOrDirectoryAction() {
    super(ActionInfo.STATUS, ScopeInfo.FILES, false);
  }

  @Override
  protected boolean filterRootsBeforeAction() {
    return true;
  }
}
