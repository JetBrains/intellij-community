// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ActionUpdaterTest extends LightPlatformTestCase {
  public void testActionGroupCanBePerformed() {
    CanBePerformedGroup canBePerformedGroup = new CanBePerformedGroup(true);
    DefaultActionGroup popupGroup = new PopupGroup(canBePerformedGroup);
    ActionGroup actionGroup = new DefaultActionGroup(popupGroup);
    List<AnAction> actions = testExpandActionGroup(actionGroup);
    assertOrderedEquals(actions, popupGroup);
  }

  public void testActionGroupCanBePerformedButNotVisible() {
    CanBePerformedGroup canBePerformedGroup = new CanBePerformedGroup(false);
    ActionGroup actionGroup = new DefaultActionGroup(new PopupGroup(canBePerformedGroup));
    List<AnAction> actions = testExpandActionGroup(actionGroup);
    assertEmpty(actions);
  }

  @NotNull
  private List<AnAction> testExpandActionGroup(ActionGroup actionGroup) {
    PresentationFactory presentationFactory = new PresentationFactory();
    DataContext dataContext = SimpleDataContext.getProjectContext(getProject());
    return Utils.expandActionGroup(false, actionGroup, presentationFactory, dataContext, ActionPlaces.UNKNOWN);
  }

  private static class PopupGroup extends DefaultActionGroup {
    private PopupGroup(AnAction @NotNull ... actions) {
      super(actions);
      setPopup(true);
    }

    @Override
    public boolean hideIfNoVisibleChildren() {
      return true;
    }
  }

  private static class CanBePerformedGroup extends DefaultActionGroup {
    private final boolean myVisible;

    private CanBePerformedGroup(boolean visible) {
      myVisible = visible;
    }

    @Override
    public boolean canBePerformed(@NotNull DataContext context) {
      return true;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(myVisible);
    }
  }
}
