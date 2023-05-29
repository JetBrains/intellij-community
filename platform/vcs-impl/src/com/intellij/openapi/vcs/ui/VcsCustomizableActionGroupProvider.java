// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ui;

import com.intellij.ide.ui.customization.CustomizableActionGroupProvider;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.vcs.VcsActions;
import com.intellij.openapi.vcs.VcsBundle;

/**
 * @author gregsh
 */
final class VcsCustomizableActionGroupProvider extends CustomizableActionGroupProvider {
  @Override
  public void registerGroups(CustomizableActionGroupRegistrar registrar) {
    registrar.addCustomizableActionGroup(VcsActions.VCS_OPERATIONS_POPUP, VcsBundle.message("vcs.operations.popup"));
    registrar.addCustomizableActionGroup(ActionPlaces.CHANGES_VIEW_TOOLBAR, VcsBundle.message("vcs.local.changes.toolbar"));
  }
}
