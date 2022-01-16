// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DocumentationComponentTest extends BasePlatformTestCase {
  public void testShowPopupAutomaticallyAction() {
    DocumentationComponent component = createDocumentationComponent();
    String actionText = CodeInsightBundle.message("javadoc.show.popup.automatically");
    ToggleAction showPopupAction = ObjectUtils.tryCast(findAction(component, actionText), ToggleAction.class);
    assertNotNull(showPopupAction);
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    boolean info = settings.AUTO_POPUP_JAVADOC_INFO;
    try {
      AnActionEvent event = createEvent();
      assertEquals(info, showPopupAction.isSelected(event));
      showPopupAction.setSelected(event, true);
      assertTrue(showPopupAction.isSelected(event));
      assertTrue(settings.AUTO_POPUP_JAVADOC_INFO);
      showPopupAction.setSelected(event, false);
      assertFalse(settings.AUTO_POPUP_JAVADOC_INFO);
      assertFalse(showPopupAction.isSelected(event));
    }
    finally {
      settings.AUTO_POPUP_JAVADOC_INFO = info;
    }
  }

  private @NotNull DocumentationComponent createDocumentationComponent() {
    DocumentationManager manager = DocumentationManager.getInstance(getProject());
    DocumentationComponent component = new DocumentationComponent(manager);
    Disposer.register(getTestRootDisposable(), component);
    return component;
  }

  private @NotNull AnActionEvent createEvent() {
    DataContext context = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, getProject())
      .build();
    return AnActionEvent.createFromDataContext("", null, context);
  }

  private @Nullable AnAction findAction(DocumentationComponent component, String actionText) {
    ActionButton gearIcon = UIUtil.findComponentOfType(component, ActionButton.class);
    assertNotNull(gearIcon);
    AnAction group = gearIcon.getAction();
    assertTrue(group instanceof ActionGroup);
    AnAction[] children = ((ActionGroup)group).getChildren(createEvent());
    return ContainerUtil.find(children, action -> action.getTemplateText().equals(actionText));
  }
}
