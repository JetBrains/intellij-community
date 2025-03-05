// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.testFramework.TestDataPath;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.devkit.DevKitIcons;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

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

  public void testMustNotProvideGutterIconsWhenInspectionDescriptionNameIsNotCaseSensitiveEqualToClassName() {
    myFixture.copyDirectoryToProject("inspectionDescriptions", "inspectionDescriptions");
    List<GutterMark> gutters = myFixture.findAllGutters("caseSensitive/MywithDescriptionInspection.java");
    assertEmpty(gutters);
  }

  public void testIntentionDescription() {
    myFixture.copyDirectoryToProject("intentionDescriptions", "intentionDescriptions");

    List<GutterMark> gutters = ContainerUtil.sorted(myFixture.findAllGutters("MyIntentionActionWithDescription.java"),
                                                    Comparator.comparing(GutterMark::getTooltipText));
    assertThat(gutters.size()).isEqualTo(2);
    DevKitGutterTargetsChecker.checkGutterTargets(gutters.get(1), "Description", DevKitIcons.Gutter.DescriptionFile, "description.html");
    DevKitGutterTargetsChecker.checkGutterTargets(gutters.get(0), "Before/After Templates", DevKitIcons.Gutter.Diff,
                                                  "after.java.template", "before.java.template");
  }

  public void testMustNotProvideGutterIconsWhenIntentionDescriptionFolderNameIsNotCaseSensitiveEqualToClassName() {
    myFixture.copyDirectoryToProject("intentionDescriptions", "intentionDescriptions");

    List<GutterMark> gutters = myFixture.findAllGutters("caseSensitive/MyIntentionActionwithDescription.java");
    assertEmpty(gutters);
  }
}