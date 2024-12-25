// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.popup.ActionPopupOptions;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.WizardPopup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlatSpeedSearchPopup extends PopupFactoryImpl.ActionGroupPopup {

  /** @deprecated Use {@link #FlatSpeedSearchPopup(String, ActionGroup, DataContext, String, Condition, boolean)} instead */
  @Deprecated(forRemoval = true)
  public FlatSpeedSearchPopup(@Nullable @NlsContexts.PopupTitle String title,
                              @NotNull ActionGroup actionGroup,
                              @NotNull DataContext dataContext,
                              @Nullable Condition<? super AnAction> preselectCondition,
                              boolean showDisableActions) {
    this(title, actionGroup, dataContext, ActionPlaces.getPopupPlace("VCS.FlatSpeedSearchPopup"),
         preselectCondition, showDisableActions);
  }

  public FlatSpeedSearchPopup(@Nullable @NlsContexts.PopupTitle String title,
                              @NotNull ActionGroup actionGroup,
                              @NotNull DataContext dataContext,
                              @NotNull String place,
                              @Nullable Condition<? super AnAction> preselectCondition,
                              boolean showDisableActions) {
    super(null, title, actionGroup, dataContext, place, new PresentationFactory(),
          ActionPopupOptions.create(false, false, showDisableActions, false, -1, false, preselectCondition), null);
  }

  protected FlatSpeedSearchPopup(@Nullable WizardPopup parent,
                                 @NotNull ListPopupStep step,
                                 @NotNull DataContext dataContext,
                                 @Nullable Object value) {
    super(parent, step, null, dataContext, -1);
    setParentValue(value);
  }

  @Override
  public final boolean shouldBeShowing(Object value) {
    if (!super.shouldBeShowing(value)) return false;
    if (!(value instanceof PopupFactoryImpl.ActionItem)) return true;
    return shouldBeShowing(((PopupFactoryImpl.ActionItem)value).getAction());
  }

  protected boolean shouldBeShowing(@NotNull AnAction action) {
    return getSpeedSearch().isHoldingFilter() || !isSpeedsearchAction(action);
  }

  public static @NotNull AnAction createSpeedSearchWrapper(@NotNull AnAction child) {
    return new MySpeedSearchAction(child);
  }

  public static @NotNull ActionGroup createSpeedSearchActionGroupWrapper(@NotNull ActionGroup child) {
    return new MySpeedSearchActionGroup(child);
  }

  protected static boolean isSpeedsearchAction(@NotNull AnAction action) {
    return action instanceof SpeedsearchAction;
  }

  protected static <T> T getSpecificAction(Object value, @NotNull Class<T> clazz) {
    if (value instanceof PopupFactoryImpl.ActionItem) {
      AnAction action = ((PopupFactoryImpl.ActionItem)value).getAction();
      if (clazz.isInstance(action)) {
        return clazz.cast(action);
      }
      else if (action instanceof ActionGroupWrapper) {
        ActionGroup group = ((ActionGroupWrapper)action).getDelegate();
        return clazz.isInstance(group) ? clazz.cast(group) : null;
      }
    }
    return null;
  }

  public interface SpeedsearchAction {
  }

  private static class MySpeedSearchAction extends AnActionWrapper implements SpeedsearchAction, DumbAware {

    MySpeedSearchAction(@NotNull AnAction action) {
      super(action);
    }
  }

  private static class MySpeedSearchActionGroup extends ActionGroupWrapper implements SpeedsearchAction, DumbAware {
    MySpeedSearchActionGroup(@NotNull ActionGroup actionGroup) {
      super(actionGroup);
      getTemplatePresentation().putClientProperty(ActionUtil.ALWAYS_VISIBLE_GROUP, true);
    }
  }
}
