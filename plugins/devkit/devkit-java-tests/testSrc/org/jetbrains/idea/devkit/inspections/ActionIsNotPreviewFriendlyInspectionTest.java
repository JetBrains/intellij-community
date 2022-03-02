// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

public class ActionIsNotPreviewFriendlyInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/actionIsNotPreviewFriendly";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ActionIsNotPreviewFriendlyInspection());
    myFixture.addClass("package com.intellij.codeInspection; public interface ProblemDescriptor {}");
    myFixture.addClass("package com.intellij.codeInsight.intention.preview; public interface IntentionPreviewInfo {}");
    myFixture.addClass("package com.intellij.openapi.project; public interface Project {}");
    myFixture.addClass("package com.intellij.codeInsight.intention;public interface FileModifier{@interface SafeFieldForPreview {}}");
    myFixture.addClass("package com.intellij.codeInspection;\n" +
                       "\n" +
                       "import com.intellij.codeInsight.intention.preview.*;\n" +
                       "import com.intellij.codeInsight.intention.*;\n" +
                       "import com.intellij.openapi.project.*;\n" +
                       "\n" +
                       "public interface LocalQuickFix extends FileModifier {\n" +
                       "  default IntentionPreviewInfo generatePreview(Project project, ProblemDescriptor pd) {\n" +
                       "    return null;\n" +
                       "  }\n" +
                       "}");
  }

  public void testMyQuickFix() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testMyQuickFixCustomGeneratePreview() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }
}
