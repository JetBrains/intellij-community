// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.vcs.ui.FlatSpeedSearchPopup;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.util.ui.JBDimension;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public class BranchLogSpeedSearchPopup extends FlatSpeedSearchPopup {
  public BranchLogSpeedSearchPopup(@NotNull ActionGroup actionGroup, @NotNull DataContext dataContext) {
    super(null, ActionGroupUtil.forceRecursiveUpdateInBackground(new DefaultActionGroup(actionGroup,
                                                                                        createSpeedSearchActionGroup(actionGroup))),
          dataContext, ActionPlaces.getPopupPlace("VCS.Log.BranchWidget"), null, false);
    setMinimumSize(new JBDimension(250, 0));
  }

  protected BranchLogSpeedSearchPopup(@Nullable WizardPopup parent,
                                      @NotNull ListPopupStep step,
                                      @NotNull DataContext context,
                                      @Nullable Object value) {
    super(parent, step, context, value);
  }

  @Override
  protected boolean shouldBeShowing(@NotNull AnAction action) {
    if (!super.shouldBeShowing(action)) return false;
    return !getSpeedSearch().isHoldingFilter() || !(action instanceof ActionGroup);
  }

  public static @NotNull ActionGroup createSpeedSearchActionGroup(@NotNull ActionGroup actionGroup) {
    List<AnAction> speedSearchActions = new ArrayList<>();
    createSpeedSearchActions(actionGroup, speedSearchActions, true);
    return new DefaultActionGroup(speedSearchActions);
  }

  private static void createSpeedSearchActions(@NotNull ActionGroup actionGroup,
                                               @NotNull List<? super AnAction> speedSearchActions,
                                               boolean isFirstLevel) {
    if (!isFirstLevel) speedSearchActions.add(Separator.create(actionGroup.getTemplatePresentation().getText()));

    for (AnAction child : actionGroup.getChildren(null)) {
      if (!isFirstLevel && !(child instanceof ActionGroup || child instanceof Separator || child instanceof SpeedsearchAction)) {
        speedSearchActions.add(createSpeedSearchWrapper(child));
      }
      else if (child instanceof ActionGroup) {
        createSpeedSearchActions((ActionGroup)child, speedSearchActions, isFirstLevel && !((ActionGroup)child).isPopup());
      }
    }
  }
}
