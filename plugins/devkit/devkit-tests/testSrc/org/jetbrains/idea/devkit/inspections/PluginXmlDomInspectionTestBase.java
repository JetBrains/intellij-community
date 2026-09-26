// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public abstract class PluginXmlDomInspectionTestBase extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.addClass("package com.intellij.openapi.components; public interface ApplicationComponent {}");
    myFixture.enableInspections(new PluginXmlDomInspection(), new XmlUnresolvedReferenceInspection());
  }

  protected final void setUpActionClasses(boolean actionGroupHasCanBePerformedMethod) {
    myFixture.addClass("package com.intellij.openapi.actionSystem; public interface DataContext {}");
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class AnAction {}");

    String actionGroupBody = !actionGroupHasCanBePerformedMethod ? "" :
                             """
                               public boolean canBePerformed(@NotNull DataContext context) {
                                 return false;
                               }
                               """;
    myFixture.addClass("""
                         package com.intellij.openapi.actionSystem;
                         
                         public abstract class ActionGroup extends AnAction {
                          %s
                         }
                         """.formatted(actionGroupBody)
    );
  }
}
