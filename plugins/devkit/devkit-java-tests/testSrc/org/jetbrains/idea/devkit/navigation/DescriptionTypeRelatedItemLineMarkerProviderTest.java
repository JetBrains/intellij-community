/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.testFramework.TestDataPath;
import icons.DevkitIcons;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@TestDataPath("$CONTENT_ROOT/testData/navigation/descriptionType")
public class DescriptionTypeRelatedItemLineMarkerProviderTest extends DescriptionTypeRelatedItemLineMarkerProviderTestBase {
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
    assertSize(2, gutters);
    Collections.sort(gutters, Comparator.comparing(GutterMark::getTooltipText));
    DevKitGutterTargetsChecker.checkGutterTargets(gutters.get(1), "Description", DevkitIcons.Gutter.DescriptionFile, "description.html");
    DevKitGutterTargetsChecker.checkGutterTargets(gutters.get(0), "Before/After Templates", DevkitIcons.Gutter.Diff,
                                                  "after.java.template", "before.java.template");
  }
}