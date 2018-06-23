// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Anchor;
import org.jetbrains.idea.devkit.inspections.quickfix.RegisterActionFix;
import org.jetbrains.idea.devkit.util.ActionData;
import org.jetbrains.idea.devkit.util.PsiUtil;

public abstract class ComponentNotRegisteredInspectionTestBase extends PluginModuleTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.addClass("package com.intellij.openapi.actionSystem; public class AnAction {}");
    myFixture.addClass("package com.intellij.openapi.components; public interface BaseComponent {}");
    myFixture.addClass("package com.intellij.openapi.components; public interface ApplicationComponent extends BaseComponent {}");

    myFixture.enableInspections(new ComponentNotRegisteredInspection());
  }

  protected abstract String getSourceFileExtension();


  public void testRegisteredAction() {
    setPluginXml("registeredAction-plugin.xml");
    myFixture.testHighlighting("RegisteredAction." + getSourceFileExtension());
  }

  public void testRegisteredActionInIDEAProject() {
    PsiUtil.markAsIdeaProject(getProject(), true);

    try {
      myFixture.copyFileToProject("registeredAction-plugin.xml", "someOtherPluginXmlName.xml");
      myFixture.testHighlighting("RegisteredAction." + getSourceFileExtension());
    }
    finally {
      PsiUtil.markAsIdeaProject(getProject(), false);
    }
  }

  public void testRegisteredActionInOptionalPluginDescriptor() {
    setPluginXml("registeredActionInOptionalPluginDescriptor-plugin.xml");
    myFixture.copyFileToProject("registeredActionInOptionalPluginDescriptor-optional-plugin.xml",
                                "META-INF/optional-plugin.xml");

    myFixture.testHighlighting("RegisteredAction." + getSourceFileExtension());
  }

  public void testRegisteredInIncludedFileAction() {
    setPluginXml("ActionXInclude.xml");
    myFixture.copyFileToProject("ActionXInclude_included.xml", "META-INF/ActionXInclude_included.xml");
    myFixture.testHighlighting("ActionXInclude." + getSourceFileExtension());
  }

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  public void testUnregisteredAction() {
    setPluginXml("unregisteredAction-plugin.xml");
    myFixture.testHighlighting("UnregisteredAction." + getSourceFileExtension());

    RegisterActionFix.ourTestActionData = new MyActionData("UnregisteredAction");
    try {
      final IntentionAction registerAction = myFixture.findSingleIntention("Register Action");
      myFixture.launchAction(registerAction);

      myFixture.checkResultByFile("META-INF/plugin.xml", "unregisteredAction-plugin_after.xml", true);
    } finally {
      RegisterActionFix.ourTestActionData = null;
    }
  }

  public void testUnregisteredActionUsedViaConstructor() {
    myFixture.testHighlighting("UnregisteredActionUsedViaConstructor." + getSourceFileExtension());
  }

  public void testRegisteredApplicationComponent() {
    setPluginXml("registeredApplicationComponent-plugin.xml");
    myFixture.testHighlighting("RegisteredApplicationComponent." + getSourceFileExtension());
  }

  public void testUnregisteredAbstractApplicationComponent() {
    myFixture.testHighlighting("UnregisteredAbstractApplicationComponent." + getSourceFileExtension());
  }

  public void testUnregisteredApplicationComponentWithoutPluginXml() {
    myFixture.testHighlighting("UnregisteredApplicationComponent." + getSourceFileExtension(),
                               "UnregisteredApplicationComponentInterface." + getSourceFileExtension());
  }

  public void testUnregisteredApplicationComponentWithRegisterFix() {
    setPluginXml("unregisteredApplicationComponent-plugin.xml");

    myFixture.testHighlighting("UnregisteredApplicationComponent." + getSourceFileExtension(),
                               "UnregisteredApplicationComponentInterface." + getSourceFileExtension());
    final IntentionAction registerAction = myFixture.findSingleIntention("Register Application Component");
    myFixture.launchAction(registerAction);

    myFixture.checkResultByFile("META-INF/plugin.xml", "unregisteredApplicationComponent-plugin_after.xml", true);
  }


  public static class MyActionData implements ActionData {
    private final String myActionClassFqn;

    public MyActionData(String actionClassFqn) {
      myActionClassFqn = actionClassFqn;
    }

    @NotNull
    @Override
    public String getActionId() {
      return StringUtil.getShortName(myActionClassFqn);
    }

    @NotNull
    @Override
    public String getActionText() {
      return "Action Text " + myActionClassFqn;
    }

    @Override
    public String getActionDescription() {
      return "Description " + myActionClassFqn;
    }

    @Nullable
    @Override
    public String getSelectedGroupId() {
      return getActionId() + "Group";
    }

    @Nullable
    @Override
    public String getSelectedActionId() {
      return getActionId() + "SelectedAction";
    }

    @Override
    public String getSelectedAnchor() {
      return Anchor.before.name();
    }

    @Nullable
    @Override
    public String getFirstKeyStroke() {
      return "1st Key " + getActionId();
    }

    @Nullable
    @Override
    public String getSecondKeyStroke() {
      return "2nd Key " + getActionId();
    }
  }
}
