/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Anchor;
import org.jetbrains.idea.devkit.inspections.quickfix.RegisterActionFix;
import org.jetbrains.idea.devkit.util.ActionData;
import org.jetbrains.idea.devkit.util.PsiUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/componentNotRegistered")
public class ComponentNotRegisteredInspectionTest extends PluginModuleTestCase {

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/inspections/componentNotRegistered";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.addClass("package com.intellij.openapi.actionSystem; public class AnAction {}");
    myFixture.addClass("package com.intellij.openapi.components; public interface BaseComponent {}");
    myFixture.addClass("package com.intellij.openapi.components; public interface ApplicationComponent extends BaseComponent {}");

    myFixture.enableInspections(new ComponentNotRegisteredInspection());
  }

  public void testRegisteredAction() {
    setPluginXml("registeredAction-plugin.xml");
    myFixture.testHighlighting("RegisteredAction.java");
  }

  public void testRegisteredActionInIDEAProject() {
    PsiUtil.markAsIdeaProject(getProject(), true);

    try {
      myFixture.copyFileToProject("registeredAction-plugin.xml", "someOtherPluginXmlName.xml");
      myFixture.testHighlighting("RegisteredAction.java");
    }
    finally {
      PsiUtil.markAsIdeaProject(getProject(), false);
    }
  }

  public void testRegisteredActionInOptionalPluginDescriptor() {
    setPluginXml("registeredActionInOptionalPluginDescriptor-plugin.xml");
    myFixture.copyFileToProject("registeredActionInOptionalPluginDescriptor-optional-plugin.xml",
                                "META-INF/optional-plugin.xml");

    myFixture.testHighlighting("RegisteredAction.java");
  }

  public void testRegisteredInIncludedFileAction() {
    setPluginXml("ActionXInclude.xml");
    myFixture.copyFileToProject("ActionXInclude_included.xml", "META-INF/ActionXInclude_included.xml");
    myFixture.testHighlighting("ActionXInclude.java");
  }

  public void testUnregisteredAction() {
    setPluginXml("unregisteredAction-plugin.xml");
    myFixture.testHighlighting("UnregisteredAction.java");

    RegisterActionFix.ourTestActionData = new MyActionData("UnregisteredAction");
    final IntentionAction registerAction = myFixture.findSingleIntention("Register Action");
    myFixture.launchAction(registerAction);

    myFixture.checkResultByFile("META-INF/plugin.xml", "unregisteredAction-plugin_after.xml", true);
  }

  public void testUnregisteredActionUsedViaConstructor() {
    myFixture.testHighlighting("UnregisteredActionUsedViaConstructor.java");
  }

  public void testRegisteredApplicationComponent() {
    setPluginXml("registeredApplicationComponent-plugin.xml");
    myFixture.testHighlighting("RegisteredApplicationComponent.java");
  }

  public void testUnregisteredAbstractApplicationComponent() {
    myFixture.testHighlighting("UnregisteredAbstractApplicationComponent.java");
  }

  public void testUnregisteredApplicationComponentWithoutPluginXml() {
    myFixture.testHighlighting("UnregisteredApplicationComponent.java",
                               "UnregisteredApplicationComponentInterface.java");
  }

  public void testUnregisteredApplicationComponentWithRegisterFix() {
    setPluginXml("unregisteredApplicationComponent-plugin.xml");

    myFixture.testHighlighting("UnregisteredApplicationComponent.java",
                               "UnregisteredApplicationComponentInterface.java");
    final IntentionAction registerAction = myFixture.findSingleIntention("Register Application Component");
    myFixture.launchAction(registerAction);

    myFixture.checkResultByFile("META-INF/plugin.xml", "unregisteredApplicationComponent-plugin_after.xml", true);
  }


  private static class MyActionData implements ActionData {
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
