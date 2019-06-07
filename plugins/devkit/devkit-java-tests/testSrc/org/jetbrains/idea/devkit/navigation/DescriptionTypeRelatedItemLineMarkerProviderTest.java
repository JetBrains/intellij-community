// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.testFramework.TestDataPath;
import icons.DevkitIcons;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@TestDataPath("$CONTENT_ROOT/testData/navigation/descriptionType")
public class DescriptionTypeRelatedItemLineMarkerProviderTest extends DescriptionTypeRelatedItemLineMarkerProviderTestBase {
  @Override
  protected boolean isIconRequired() {
    return true;
  }

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "navigation/descriptionType";
  }

  public void testInspectionDescription() {
    doTestInspectionDescription("MyWithDescriptionInspection.java", "MyWithDescription.html");
  }

  public void testInspectionDescriptionFromFieldReference() {
    doTestInspectionDescription("MyWithDescriptionFromFieldReferenceInspection.java", "MyWithDescriptionFromFieldReferenceInspection.html");
  }

  public void testIntentionDescription() {
    myFixture.copyDirectoryToProject("intentionDescriptions", "intentionDescriptions");

    List<GutterMark> gutters = myFixture.findAllGutters("MyIntentionActionWithDescription.java");
    assertThat(gutters.size()).isEqualTo(2);
    Collections.sort(gutters, Comparator.comparing(GutterMark::getTooltipText));
    DevKitGutterTargetsChecker.checkGutterTargets(gutters.get(1), "Description", DevkitIcons.Gutter.DescriptionFile, "description.html");
    DevKitGutterTargetsChecker.checkGutterTargets(gutters.get(0), "Before/After Templates", DevkitIcons.Gutter.Diff,
                                                  "after.java.template", "before.java.template");
  }
}