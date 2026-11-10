// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class ActionIsNotPreviewFriendlyInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/actionIsNotPreviewFriendly");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new ActionIsNotPreviewFriendlyInspection());
    myFixture.addClass("package com.intellij.codeInspection; public interface ProblemDescriptor {}");
    myFixture.addClass("package com.intellij.codeInsight.intention.preview; public interface IntentionPreviewInfo {}");
    myFixture.addClass("package com.intellij.openapi.project; public interface Project {}");
    myFixture.addClass("""
                         package com.intellij.codeInsight.intention;public interface FileModifier{
                         @interface SafeFieldForPreview {}
                         @interface SafeTypeForPreview {}
                         }""");
    myFixture.addClass("""
                         package com.intellij.codeInspection;

                         import com.intellij.codeInsight.intention.preview.*;
                         import com.intellij.codeInsight.intention.*;
                         import com.intellij.openapi.project.*;

                         public interface LocalQuickFix extends FileModifier {
                           default IntentionPreviewInfo generatePreview(Project project, ProblemDescriptor pd) {
                             return null;
                           }
                         }""");
    myFixture.addClass("""
                         package com.intellij.codeInsight.intention;

                         import com.intellij.codeInsight.intention.*;

                         public interface IntentionAction extends FileModifier {
                         }""");
  }

  @Test
  public void testMyQuickFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testMyIntentionAction() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testMyQuickFixCustomGeneratePreview() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }
}
