/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.popup.PopupFactoryImpl;
import org.jetbrains.annotations.NotNull;

public class FlatSpeedSearchPopup extends PopupFactoryImpl.ActionGroupPopup {
  public FlatSpeedSearchPopup(@NotNull ActionGroup actionGroup,
                              @NotNull ActionGroup speedSearchActionGroup,
                              @NotNull DataContext dataContext) {
    super(null, new DefaultActionGroup(actionGroup, speedSearchActionGroup), dataContext, false, false, false, false, null, -1, null, null);
  }

  public FlatSpeedSearchPopup(@NotNull ActionGroup actionGroup, @NotNull DataContext dataContext) {
    this(actionGroup, createSpeedSearchActionGroup(actionGroup), dataContext);
  }

  @NotNull
  public static ActionGroup createSpeedSearchActionGroup(@NotNull ActionGroup actionGroup) {
    DefaultActionGroup speedSearchActions = new DefaultActionGroup();
    createSpeedSearchActions(actionGroup, speedSearchActions, true);
    return speedSearchActions;
  }

  private static void createSpeedSearchActions(@NotNull ActionGroup actionGroup,
                                               @NotNull DefaultActionGroup speedSearchActions,
                                               boolean isFirstLevel) {
    if (!isFirstLevel) speedSearchActions.addSeparator(actionGroup.getTemplatePresentation().getText());

    for (AnAction child : actionGroup.getChildren(null)) {
      if (!isFirstLevel && !(child instanceof ActionGroup || child instanceof Separator || child instanceof SpeedsearchAction)) {
        speedSearchActions.add(createSpeedSearchWrapper(child));
      }
      else if (child instanceof ActionGroup) {
        createSpeedSearchActions((ActionGroup)child, speedSearchActions, isFirstLevel && !((ActionGroup)child).isPopup());
      }
    }
  }

  @NotNull
  public static AnAction createSpeedSearchWrapper(@NotNull AnAction child) {
    return new MySpeedSearchAction(child);
  }

  @Override
  public boolean shouldBeShowing(Object value) {
    if (!super.shouldBeShowing(value)) return false;
    if (!(value instanceof PopupFactoryImpl.ActionItem)) return true;

    AnAction action = ((PopupFactoryImpl.ActionItem)value).getAction();
    if (getSpeedSearch().isHoldingFilter()) {
      return !(action instanceof ActionGroup);
    }
    else {
      return !isSpeedsearchAction(action);
    }
  }

  private static boolean isSpeedsearchAction(@NotNull AnAction action) {
    return action instanceof SpeedsearchAction;
  }

  public interface SpeedsearchAction {
  }

  private static class MySpeedSearchAction extends AnAction implements SpeedsearchAction {
    @NotNull private final AnAction myAction;

    public MySpeedSearchAction(@NotNull AnAction action) {
      myAction = action;
      copyFrom(action);
    }

    @Override
    public boolean isDumbAware() {
      return myAction.isDumbAware();
    }

    @Override
    public void update(AnActionEvent e) {
      myAction.update(e);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myAction.actionPerformed(e);
    }
  }
}
