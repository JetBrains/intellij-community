// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ActionUpdaterTest extends LightPlatformTestCase {
  public void testActionGroupCanBePerformed() {
    ActionGroup canBePerformedGroup = newCanBePerformedGroup(true, true);
    ActionGroup popupGroup = newPopupGroup(canBePerformedGroup);
    ActionGroup actionGroup = new DefaultActionGroup(popupGroup);
    List<AnAction> actions = testExpandActionGroup(actionGroup);
    assertOrderedEquals(actions, popupGroup);
  }

  public void testActionGroupCanBePerformedButNotVisible() {
    ActionGroup canBePerformedGroup = newCanBePerformedGroup(false, false);
    ActionGroup actionGroup = new DefaultActionGroup(newPopupGroup(canBePerformedGroup));
    List<AnAction> actions = testExpandActionGroup(actionGroup);
    assertEmpty(actions);
  }

  public void testActionGroupCanBePerformedButNotEnabled() {
    ActionGroup canBePerformedGroup = newCanBePerformedGroup(true, false);
    ActionGroup actionGroup = new DefaultCompactActionGroup(newPopupGroup(canBePerformedGroup));
    List<AnAction> actions = testExpandActionGroup(actionGroup);
    assertEmpty(actions);
  }

  public void testWrappedActionGroupHasCorrectPresentation() {
    String customizedText = "Customized!";
    PresentationFactory presentationFactory = new PresentationFactory();
    ActionGroup popupGroup = new DefaultActionGroup(newCanBePerformedGroup(true, true)) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setText(customizedText);
      }
    };
    popupGroup.getTemplatePresentation().setPopupGroup(true);
    List<AnAction> actions = testExpandActionGroup(new DefaultCompactActionGroup(popupGroup), presentationFactory);
    AnAction actual = ContainerUtil.getOnlyItem(actions);
    assertTrue("wrapper expected", actual instanceof ActionGroupWrapper wrapper && wrapper.getDelegate() == popupGroup);
    Presentation actualPresentation = presentationFactory.getPresentation(actual);
    assertSame(customizedText, actualPresentation.getText());
    assertSame(actualPresentation, presentationFactory.getPresentation(popupGroup));
  }

  @NotNull
  private List<AnAction> testExpandActionGroup(@NotNull ActionGroup actionGroup) {
    return testExpandActionGroup(actionGroup, new PresentationFactory());
  }

  @NotNull
  private List<AnAction> testExpandActionGroup(@NotNull ActionGroup actionGroup,
                                               @NotNull PresentationFactory presentationFactory) {
    DataContext dataContext = SimpleDataContext.getProjectContext(getProject());
    return Utils.expandActionGroup(actionGroup, presentationFactory, dataContext, ActionPlaces.UNKNOWN);
  }

  private static ActionGroup newPopupGroup(AnAction @NotNull ... actions) {
    DefaultActionGroup group = new DefaultActionGroup(actions);
    group.getTemplatePresentation().setPopupGroup(true);
    group.getTemplatePresentation().setHideGroupIfEmpty(true);
    return group;
  }

  private static DefaultActionGroup newCanBePerformedGroup(boolean visible, boolean enabled) {
    return new DefaultActionGroup() {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setVisible(visible);
        e.getPresentation().setEnabled(enabled);
        e.getPresentation().setPerformGroup(true);
      }
    };
  }
}
